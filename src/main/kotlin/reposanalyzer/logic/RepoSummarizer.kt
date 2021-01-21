package reposanalyzer.logic

import astminer.cli.normalizeParseResult
import astminer.common.model.Node
import astminer.common.model.Parser
import astminer.common.preOrder
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import reposanalyzer.config.AnalysisConfig
import reposanalyzer.config.CommitsType
import reposanalyzer.config.Language
import reposanalyzer.git.checkoutCommit
import reposanalyzer.git.checkoutHashOrName
import reposanalyzer.git.getCommitsDiff
import reposanalyzer.git.getDiffFiles
import reposanalyzer.git.isRepoCloned
import reposanalyzer.methods.MethodSummaryStorage
import reposanalyzer.methods.summarizers.MethodSummarizersFactory
import reposanalyzer.parsing.LabelExtractorFactory
import reposanalyzer.utils.CommitsLogger
import reposanalyzer.utils.WorkLogger
import reposanalyzer.utils.deleteDirectory
import reposanalyzer.utils.getAbsolutePatches
import reposanalyzer.utils.getNotHiddenNotDirectoryFiles
import reposanalyzer.utils.readFileToString
import reposanalyzer.utils.removePrefixPath
import reposanalyzer.zipper.Zipper
import java.io.File
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class RepoSummarizer(
    private val analysisRepository: AnalysisRepository,
    private val dumpPath: String,
    private val parsers: ConcurrentHashMap<Language, Parser<out Node>>,
    private val config: AnalysisConfig
) : Runnable, Zipper {

    private companion object {
        const val METHODS_SUMMARY_FILE = "methods.jsonl"
        const val COMMITS_LOG = "commits_log.jsonl"
        const val WORK_LOG = "work_log.txt"
        const val FIRST_HASH = 7
    }

    enum class Status {
        NOT_INITIALIZED,
        LOADED,
        RUNNING,
        DONE,
        REPO_NOT_PRESENT,
        INTERRUPTED,
        EMPTY_HISTORY,
        SMALL_MERGES_PART,
        INIT_ERROR,
        INIT_BAD_DEF_BRANCH_ERROR,
        WORK_ERROR
    }

    @Volatile var status = Status.NOT_INITIALIZED

    private var currCommit: RevCommit? = null
    private var prevCommit: RevCommit? = null
    private var defaultBranchHead: Ref? = null
    private val commitsHistory = mutableListOf<RevCommit>()

    private lateinit var summaryStorage: MethodSummaryStorage
    private lateinit var commitsLogger: CommitsLogger
    private lateinit var workLogger: WorkLogger

    private lateinit var repository: Repository
    private lateinit var git: Git

    override fun run() {
        init()
        if (status != Status.LOADED) {
            workLogger.add("> SUMMARIZER NOT LOADED: $status")
            return
        }
        status = Status.RUNNING
        status = try {
            workLogger.add("> search started at ${Date(System.currentTimeMillis())}")
            processCommits()
            git.checkoutHashOrName(defaultBranchHead?.name) // back to normal head
            workLogger.add("> back to start HEAD: ${defaultBranchHead?.name}")
            workLogger.add("> search ended at ${Date(System.currentTimeMillis())}")
            if (status == Status.RUNNING) Status.DONE else status
        } catch (e: Exception) {
            workLogger.add("========= WORKER RUNNING ERROR FOR $analysisRepository =========")
            workLogger.add(e.stackTraceToString())
            Status.WORK_ERROR
        } finally {
            try {
                dump()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun init() {
        try {
            initStorageAndLogs()
            status = if (!analysisRepository.initRepository(dumpPath)) {
                Status.REPO_NOT_PRESENT
            } else if (!analysisRepository.loadDefaultBranchHead()) {
                Status.INIT_BAD_DEF_BRANCH_ERROR
            } else {
                git = analysisRepository.git
                repository = analysisRepository.repository
                defaultBranchHead = analysisRepository.defaultBranchHead
                loadHistory()
                if (commitsHistory.isEmpty()) {
                    Status.EMPTY_HISTORY
                } else if (analysisRepository.mergesPart < config.mergesPart) {
                    Status.SMALL_MERGES_PART
                } else {
                    Status.LOADED
                }
            }
            workLogger.add("> init: default repo branch: ${defaultBranchHead?.name}")
            workLogger.add(
                "> init: commits count [merge: ${analysisRepository.mergeCommitsNumber}, " +
                    "first-parents: ${analysisRepository.firstParentsCommitsNumber}]"
            )
            when (status) {
                Status.INIT_BAD_DEF_BRANCH_ERROR -> workLogger.add("> BAD DEFAULT BRANCH: $analysisRepository")
                Status.EMPTY_HISTORY -> workLogger.add("> NO HISTORY FOR REPOSITORY: $analysisRepository")
                Status.LOADED -> workLogger.add("> SUCCESSFUL LOADED: $analysisRepository")
                else -> {} // ignore
            }
        } catch (e: Exception) {
            workLogger.add("========= WORKER INIT ERROR: $analysisRepository =========")
            workLogger.add(e.stackTraceToString())
            status = Status.INIT_ERROR
        } finally {
            workLogger.dump()
        }
    }

    private fun initStorageAndLogs() {
        File(dumpPath).mkdirs()
        val workLogPath = File(dumpPath).resolve(WORK_LOG).absolutePath
        val commitsLogPath = File(dumpPath).resolve(COMMITS_LOG).absolutePath
        val methodsSummaryPath = File(dumpPath).resolve(METHODS_SUMMARY_FILE).absolutePath
        workLogger = WorkLogger(workLogPath, config.isDebug)
        commitsLogger = CommitsLogger(commitsLogPath, config.logDumpThreshold)
        summaryStorage = MethodSummaryStorage(methodsSummaryPath, config.summaryDumpThreshold, workLogger)
    }

    private fun initRepository(): Boolean {
        val isRepoPresent: Boolean = if (analysisRepository.path.isRepoCloned()) {
            analysisRepository.openRepositoryByDotGitDir()
            true
        } else {
            analysisRepository.cloneRepository(dumpPath)
        }
        if (isRepoPresent) {
            git = analysisRepository.git
            repository = analysisRepository.repository
            defaultBranchHead = analysisRepository.defaultBranchHead
        }
        return isRepoPresent
    }

    private fun processCommits() {
        currCommit = commitsHistory.firstOrNull() ?: return // log must be not empty by init state
        currCommit?.processCommit(currCommit, analysisRepository.path, filesPatches = listOf()) // process first commit
        for (i in 1 until commitsHistory.size) { // process others commits
            if (status != Status.RUNNING) break
            prevCommit = currCommit
            currCommit = commitsHistory[i]
            val diff = git.getCommitsDiff(repository.newObjectReader(), currCommit, prevCommit)
            val processedDiff = diff.getDiffFiles(repository, config.copyDetection)
            val supportedFiles = processedDiff.getSupportedFiles(config.supportedExtensions)
            val filesPatches = supportedFiles.getAbsolutePatches(analysisRepository.path) // supported extensions files
            workLogger.add(
                "> files diff [${prevCommit?.name?.substring(0, FIRST_HASH)}, " +
                    "${currCommit?.name?.substring(0, FIRST_HASH)}, " +
                    "total: ${diff.size}, supported: ${filesPatches.size}]"
            )
            if (filesPatches.isEmpty()) {
                commitsLogger.add(currCommit, prevCommit, mapOf()) // no files to parse => no checkout
            } else {
                currCommit?.processCommit(prevCommit, filesPatches = filesPatches)
            }
        }
    }

    private fun RevCommit.processCommit(
        prevCommit: RevCommit? = null,
        dirPath: String? = null,
        filesPatches: List<String> = listOf()
    ) {
        workLogger.add("> checkout on [${this.name.substring(0, FIRST_HASH)}, ${this.shortMessage}]")
        git.checkoutCommit(this) // checkout
        val files = if (dirPath != null) {
            getNotHiddenNotDirectoryFiles(analysisRepository.path)
        } else {
            getNotHiddenNotDirectoryFiles(filesPatches)
        }
        val filesByLang = files.getFilesByLanguage(config.languages)
        filesByLang.parseFilesByLanguage()
        commitsLogger.add(
            this, prevCommit,
            filesByLang.removePrefixPath(analysisRepository.path + File.separator)
        )
    }

    private fun Map<Language, List<File>>.parseFilesByLanguage() =
        this.filter { (_, files) -> files.isNotEmpty() }
            .forEach { (lang, files) -> files.parseFiles(lang) }

    private fun List<File>.parseFiles(language: Language) {
        val parser = parsers[language]
        if (parser == null) {
            workLogger.add("> unsupported language $language -- no parser")
            return
        }
        val labelExtractor = LabelExtractorFactory.getLabelExtractor(
            config.task, config.granularity, config.hideMethodName, config.filterPredicates
        )
        val summarizer = MethodSummarizersFactory.getMethodSummarizer(language, config.hideMethodName)
        parser.parseFiles(this) { parseResult ->
            val fileContent = parseResult.filePath.readFileToString()
            val fileLinesStarts = parseResult.filePath.getFileLinesLength().calculateLinesStarts()
            val relativePath = parseResult.filePath
                .removePrefix(analysisRepository.path + File.separator)
                .splitToParents()
                .joinToString("/")

            normalizeParseResult(parseResult, true)
            val labeledParseResults = labelExtractor.toLabeledData(parseResult)
            labeledParseResults.forEach { (root, label) ->
                val methodFullName = summarizer.getMethodFullName(root, label)
                if (summaryStorage.contains(methodFullName, parseResult.filePath)) {
                    return@forEach
                }
                root.preOrder().forEach { node ->
                    config.excludeNodes.forEach {
                        node.removeChildrenOfType(it)
                    }
                }
                val methodSummary = summarizer.summarize(
                    root,
                    label,
                    fileContent,
                    relativePath,
                    fileLinesStarts
                )
                methodSummary.repoOwner = analysisRepository.owner
                methodSummary.repoName = analysisRepository.name
                methodSummary.commit = currCommit
                summaryStorage.add(methodSummary)
            }
        }
    }

    private fun dump() {
        analysisRepository.dump(File(dumpPath).resolve("repo_info.json").absolutePath)
        summaryStorage.dump()
        summaryStorage.clear()
        commitsLogger.dump()
        commitsLogger.clear()
        workLogger.dump()
        analysisRepository.clear()
        if (status != Status.REPO_NOT_PRESENT) {
            if (config.removeRepoAfterAnalysis) {
                analysisRepository.path.deleteDirectory()
            }
            if (config.zipFiles) {
                compressFolder(File(dumpPath), config.removeAfterZip)
            }
        }
    }

    private fun loadHistory() {
        analysisRepository.loadCommitsHistory()
        when (config.commitsType) {
            CommitsType.ONLY_MERGES -> commitsHistory.addAll(analysisRepository.mergeCommits)
            CommitsType.FIRST_PARENTS_INCLUDE_MERGES -> commitsHistory.addAll(analysisRepository.firstParentsCommits)
        }
        commitsHistory.reverse() // reverse history == from oldest commit to newest
    }
}
