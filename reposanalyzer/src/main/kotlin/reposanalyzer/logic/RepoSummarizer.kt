package reposanalyzer.logic

import astminer.cli.normalizeParseResult
import astminer.common.preOrder
import org.apache.commons.io.FileUtils
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
import reposanalyzer.git.getFirstParentHistory
import reposanalyzer.git.getMergeCommitsHistory
import reposanalyzer.git.openRepositoryByDotGitDir
import reposanalyzer.methods.MethodSummaryStorage
import reposanalyzer.methods.summarizers.MethodSummarizersFactory
import reposanalyzer.parsing.GumTreeParserFactory
import reposanalyzer.parsing.LabelExtractorFactory
import reposanalyzer.utils.WorkLogger
import reposanalyzer.utils.getAbsolutePatches
import reposanalyzer.utils.getNotHiddenNotDirectoryFiles
import reposanalyzer.utils.readFileToString
import reposanalyzer.utils.removePrefixPath
import reposanalyzer.zipper.Zipper
import java.io.File
import java.io.IOException
import java.util.Date

class RepoSummarizer(
    private val repoInfo: RepoInfo,
    private val dumpPath: String,
    private val config: AnalysisConfig
) : Runnable, Zipper {

    private companion object {
        const val METHODS_SUMMARY_FILE = "methods.jsonl"
        const val COMMITS_LOG = "commits_log.jsonl"
        const val WORK_LOG = "work_log.txt"
        const val LOADED_REPO = "LOADED_REPO"

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
        INIT_ERROR,
        INIT_BAD_DEF_BRANCH_ERROR,
        WORK_ERROR
    }

    @Volatile var status = Status.NOT_INITIALIZED

    private val parserFactory = GumTreeParserFactory()
    private val labelExtractorFactory = LabelExtractorFactory()
    private val methodSummarizerFactory = MethodSummarizersFactory()

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
            workLogger.add("> SUMMARIZER IS NOT LOADED")
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
            workLogger.add("========= WORKER RUNNING ERROR FOR $repoInfo =========")
            workLogger.add(e.stackTraceToString())
            Status.WORK_ERROR
        } finally {
            try {
                dump()
                git.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun init() {
        try {
            initStorageAndLogs()
            initRepository()
            if (status == Status.REPO_NOT_PRESENT) {
                return
            }
            status = when (defaultBranchHead) {
                null -> Status.INIT_BAD_DEF_BRANCH_ERROR
                else -> {
                    loadHistory()
                    workLogger.add("> init: default repo branch: ${defaultBranchHead?.name}")
                    workLogger.add("> init: commits count: ${commitsHistory.size}")
                    if (commitsHistory.isEmpty()) Status.EMPTY_HISTORY else Status.LOADED
                }
            }
            when (status) {
                Status.INIT_BAD_DEF_BRANCH_ERROR -> workLogger.add("> BAD DEFAULT BRANCH FOR $repoInfo")
                Status.EMPTY_HISTORY -> workLogger.add("> init: no history for $repoInfo")
                Status.LOADED -> workLogger.add("> init: successful loaded for $repoInfo")
                else -> {} // ignore
            }
        } catch (e: Exception) {
            workLogger.add("========= WORKER INIT ERROR FOR $repoInfo =========")
            workLogger.add(e.stackTraceToString())
            status = Status.INIT_ERROR
        } finally {
            workLogger.dump()
        }
    }

    private fun initStorageAndLogs() {
        File(dumpPath).mkdirs()
        val workLogPath = dumpPath + File.separator + WORK_LOG
        val commitsLogPath = dumpPath + File.separator + COMMITS_LOG
        val methodsSummaryPath = dumpPath + File.separator + METHODS_SUMMARY_FILE
        workLogger = WorkLogger(workLogPath, config.isDebug)
        commitsLogger = CommitsLogger(commitsLogPath, config.logDumpThreshold)
        summaryStorage = MethodSummaryStorage(methodsSummaryPath, config.summaryDumpThreshold, workLogger)
    }

    private fun initRepository() {
        if (repoInfo.path.isEmpty()) {
            val url = repoInfo.constructLoadUrl()
            if (url == null) {
                status = Status.REPO_NOT_PRESENT
                return
            }
            repoInfo.path = dumpPath + File.separator + LOADED_REPO
            val dir = File(repoInfo.path)
            if (dir.exists()) FileUtils.deleteDirectory(dir)
            git = Git.cloneRepository()
                .setURI(url)
                .setDirectory(dir)
                .setCloneAllBranches(true)
                .call()
            repository = git.repository
            defaultBranchHead = repository.findRef(repository.fullBranch)
        } else {
            if (!File(repoInfo.path).exists()) {
                status = Status.REPO_NOT_PRESENT
                return
            }
            repository = repoInfo.dotGitPath.openRepositoryByDotGitDir()
            git = Git(repository)
            defaultBranchHead = repository.findRef(repository.fullBranch)
        }
    }

    private fun processCommits() {
        currCommit = commitsHistory.firstOrNull() ?: return // log must be not empty by init state
        currCommit?.processCommit(currCommit, repoInfo.path, filesPatches = listOf()) // process first commit
        for (i in 1 until commitsHistory.size) { // process others commits
            if (status != Status.RUNNING) break
            prevCommit = currCommit
            currCommit = commitsHistory[i]
            val diff = git.getCommitsDiff(repository.newObjectReader(), currCommit, prevCommit)
            val processedDiff = diff.getDiffFiles(repository, config.copyDetection)
            val supportedFiles = processedDiff.getSupportedFiles(config.supportedExtensions)
            val filesPatches = supportedFiles.getAbsolutePatches(repoInfo.path) // files with supported extension
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
            getNotHiddenNotDirectoryFiles(repoInfo.path)
        } else {
            getNotHiddenNotDirectoryFiles(filesPatches)
        }
        val filesByLang = files.getFilesByLanguage(config.languages)
        filesByLang.parseFilesByLanguage()
        commitsLogger.add(
            this, prevCommit,
            filesByLang.removePrefixPath(repoInfo.path + File.separator)
        )
    }

    private fun Map<Language, List<File>>.parseFilesByLanguage() =
        this.forEach { (lang, files) -> if (files.isNotEmpty()) { files.parseFiles(lang) } }

    private fun List<File>.parseFiles(language: Language) {
        val parser = parserFactory.getParser(language)
        val labelExtractor = labelExtractorFactory.getLabelExtractor(
            config.task, config.granularity, config.hideMethodName, config.filterPredicates
        )
        val summarizer = methodSummarizerFactory.getMethodSummarizer(language, config.hideMethodName)
        parser.parseFiles(this) { parseResult ->
            val fileContent = parseResult.filePath.readFileToString()
            val relativePath = parseResult.filePath.removePrefix(repoInfo.path + File.separator)

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
                    relativePath
                )
                methodSummary.commit = currCommit
                summaryStorage.add(methodSummary)
            }
        }
    }

    private fun dump() {
        summaryStorage.dump()
        summaryStorage.clear()
        commitsLogger.dump()
        commitsLogger.clear()
        workLogger.dump()
        if (status != Status.REPO_NOT_PRESENT) {
            if (config.removeRepoAfterAnalysis) {
                try {
                    FileUtils.deleteDirectory(File(repoInfo.path))
                } catch (e: IOException) {
                    // ignore
                }
            }
            if (config.zipFiles) {
                try {
                    compressFolder(File(dumpPath), config.removeAfterZip)
                } catch (e: IOException) {
                    // ignore
                }
            }
        }
    }

    private fun loadHistory() {
        when (config.commitsType) {
            CommitsType.ONLY_MERGES -> defaultBranchHead?.let { head ->
                commitsHistory.addAll(repository.getMergeCommitsHistory(head.objectId))
            }
            CommitsType.FIRST_PARENTS_INCLUDE_MERGES -> defaultBranchHead?.let { head ->
                commitsHistory.addAll(repository.getFirstParentHistory(head.objectId))
            }
        }
        commitsHistory.reverse() // reverse history == from oldest commit to newest
    }
}
