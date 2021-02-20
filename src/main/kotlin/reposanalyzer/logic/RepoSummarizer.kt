package reposanalyzer.logic

import astminer.common.model.Node
import astminer.common.model.Parser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import reposanalyzer.methods.MethodSummaryStorage
import reposanalyzer.parsing.MethodParseProvider
import reposanalyzer.utils.CommitsLogger
import reposanalyzer.utils.WorkLogger
import reposanalyzer.utils.deleteDirectory
import reposanalyzer.utils.getAbsolutePatches
import reposanalyzer.utils.getNotHiddenNotDirectoryFiles
import reposanalyzer.utils.removePrefixPath
import reposanalyzer.utils.prettyDate
import reposanalyzer.zipper.Zipper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class RepoSummarizer(
    val analysisRepo: AnalysisRepository,
    private val dumpPath: String,
    private val parsers: ConcurrentHashMap<Language, Parser<out Node>>,
    private val config: AnalysisConfig
) : Runnable, Zipper {

    private companion object {
        const val REPO_INFO = "repo_info.json"
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
        SMALL_COMMITS_NUMBER,
        SMALL_MERGES_PART,
        INIT_ERROR,
        INIT_BAD_DEF_BRANCH_ERROR,
        WORK_ERROR
    }

    @Volatile var status = Status.NOT_INITIALIZED

    private var analysisStart: Long = 0
    private var analysisEnd: Long = 0

    private var currCommit: RevCommit? = null
    private var prevCommit: RevCommit? = null
    private var defaultBranchHead: Ref? = null
    private val commitsHistory = mutableListOf<RevCommit>()

    private lateinit var methodParseProvider: MethodParseProvider
    private lateinit var summaryStorage: MethodSummaryStorage
    private lateinit var commitsLogger: CommitsLogger
    private lateinit var workLogger: WorkLogger

    private lateinit var repository: Repository
    private lateinit var git: Git

    override fun run() {
        analysisStart = System.currentTimeMillis()
        init()
        dumpRepoSummary() // dump repo summary before running
        if (status != Status.LOADED) {
            workLogger.add("> SUMMARIZER NOT LOADED: $status")
            return
        }
        status = Status.RUNNING
        try {
            workLogger.add("> search started at ${prettyDate(System.currentTimeMillis())}")
            processCommits()
            analysisEnd = System.currentTimeMillis()
            git.checkoutHashOrName(defaultBranchHead?.name) // back to normal head
            workLogger.add("> back to start HEAD: ${defaultBranchHead?.name}")
            workLogger.add("> search ended at ${prettyDate(System.currentTimeMillis())}")
            if (status == Status.RUNNING) {
                status = Status.DONE
            }
        } catch (e: Exception) {
            workLogger.add("========= WORKER RUNNING ERROR FOR $analysisRepo =========")
            workLogger.add(e.stackTraceToString())
            status = Status.WORK_ERROR
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
            status = if (!analysisRepo.initRepository(dumpPath)) {
                Status.REPO_NOT_PRESENT
            } else if (!analysisRepo.loadDefaultBranchHead()) {
                Status.INIT_BAD_DEF_BRANCH_ERROR
            } else {
                git = analysisRepo.git
                repository = analysisRepo.repository
                defaultBranchHead = analysisRepo.defaultBranchHead
                loadHistory()
            }
            workLogger.add("> init: default repo branch: ${defaultBranchHead?.name}")
            workLogger.add(
                "> init: commits count [merge: ${analysisRepo.mergeCommitsNumber}, " +
                    "first-parents: ${analysisRepo.firstParentsCommitsNumber}]"
            )
            when (status) {
                Status.INIT_BAD_DEF_BRANCH_ERROR -> workLogger.add("> BAD DEFAULT BRANCH: $analysisRepo")
                Status.EMPTY_HISTORY -> workLogger.add("> NO HISTORY FOR REPOSITORY: $analysisRepo")
                Status.LOADED -> workLogger.add("> SUCCESSFUL LOADED: $analysisRepo")
                else -> {} // ignore
            }
        } catch (e: Exception) {
            workLogger.add("========= WORKER INIT ERROR: $analysisRepo =========")
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
        workLogger = WorkLogger(workLogPath, config.isDebugSummarizers)
        commitsLogger = CommitsLogger(commitsLogPath, config.logDumpThreshold)
        summaryStorage = MethodSummaryStorage(
            dumpPath, config.isAstDotFormat, config.isCode2SeqDump, config.summaryDumpThreshold, workLogger
        )
        methodParseProvider = MethodParseProvider(summaryStorage, config, analysisRepo)
    }

    private fun processCommits() {
        currCommit = commitsHistory.firstOrNull() ?: return // log must be not empty by init state
        currCommit?.processCommit(currCommit, analysisRepo.path, filesPaths = listOf()) // process first commit
        for (i in 1 until commitsHistory.size) { // process others commits
            if (status != Status.RUNNING) break
            prevCommit = currCommit
            currCommit = commitsHistory[i]
            val diff = git.getCommitsDiff(repository.newObjectReader(), currCommit, prevCommit)
            val processedDiff = diff.getDiffFiles(repository, config.copyDetection)
            val supportedFiles = processedDiff.getSupportedFiles(config.supportedExtensions)
            val filesPaths = supportedFiles.getAbsolutePatches(analysisRepo.path) // supported extensions files
            workLogger.add(
                "> files diff [${prevCommit?.name?.substring(0, FIRST_HASH)}, " +
                    "${currCommit?.name?.substring(0, FIRST_HASH)}, " +
                    "total: ${diff.size}, supported: ${filesPaths.size}]"
            )
            if (filesPaths.isEmpty()) {
                commitsLogger.add(currCommit, prevCommit, mapOf()) // no files to parse => no checkout
            } else {
                currCommit?.processCommit(prevCommit, filesPaths = filesPaths)
            }
        }
    }

    private fun RevCommit.processCommit(
        prevCommit: RevCommit? = null,
        dirPath: String? = null,
        filesPaths: List<String> = listOf()
    ) {
        workLogger.add("> checkout on [${this.name.substring(0, FIRST_HASH)}, ${this.shortMessage}]")
        git.checkoutCommit(this) // checkout
        val files = if (dirPath != null) {
            getNotHiddenNotDirectoryFiles(analysisRepo.path)
        } else {
            getNotHiddenNotDirectoryFiles(filesPaths)
        }
        val filesByLang = files.getFilesByLanguage(config.languages)
        filesByLang.parseFilesByLanguage()
        commitsLogger.add(
            this, prevCommit,
            filesByLang.removePrefixPath(analysisRepo.path + File.separator)
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
        methodParseProvider.parse(parser, this, language, analysisRepo.path, currCommit)
    }

    private fun dumpRepoSummary() {
        val secondsSpent = (analysisEnd - analysisStart) / 1000L
        val stats = summaryStorage.stats
        val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        val jsonNode = analysisRepo.toJSON(mapper) as ObjectNode
        jsonNode.set<JsonNode>("processed_commits", mapper.valueToTree(commitsHistory.size))
        jsonNode.set<JsonNode>("total_methods", mapper.valueToTree(stats.totalMethods))
        jsonNode.set<JsonNode>("total_uniq_full_names", mapper.valueToTree(stats.totalUniqMethodsFullNames))
        jsonNode.set<JsonNode>("total_paths", mapper.valueToTree(stats.pathsNumber))
        jsonNode.set<JsonNode>("methods_with_doc", mapper.valueToTree(stats.methodsWithDoc))
        jsonNode.set<JsonNode>("methods_with_comment", mapper.valueToTree(stats.methodsWithComment))
        jsonNode.set<JsonNode>("mean_lines_length", mapper.valueToTree(stats.meanLinesLength))
        jsonNode.set<JsonNode>("processed_files", mapper.valueToTree(stats.totalFiles))
        jsonNode.set<JsonNode>("analysis_languages", mapper.valueToTree(config.languages))
        jsonNode.set<JsonNode>("process_end_status", mapper.valueToTree(status))
        jsonNode.set<JsonNode>("seconds_spent", mapper.valueToTree(if (secondsSpent >= 0) secondsSpent else 0))
        mapper.writeValue(FileOutputStream(File(dumpPath).resolve(REPO_INFO), false), jsonNode)
    }

    private fun dump() {
        workLogger.add(
            "> TOTAL DUMPS [${summaryStorage.stats.totalMethods} methods," +
                " ${summaryStorage.stats.pathsNumber} paths]"
        )
        dumpRepoSummary()
        summaryStorage.dump()
        summaryStorage.dumpVisited()
        summaryStorage.clear()
        commitsLogger.dump()
        commitsLogger.clear()
        workLogger.dump()
        analysisRepo.clear()
        if (status != Status.REPO_NOT_PRESENT) {
            if (config.removeRepoAfterAnalysis) {
                analysisRepo.path.deleteDirectory()
            }
            if (config.zipFiles) {
                compressFolder(File(dumpPath), listOf(REPO_INFO, WORK_LOG), config.removeAfterZip)
            }
        }
    }

    private fun loadHistory(): Status {
        analysisRepo.loadCommitsHistory()
        when (config.commitsType) {
            CommitsType.ONLY_MERGES -> commitsHistory.addAll(analysisRepo.mergeHistory.history)
            CommitsType.FIRST_PARENTS_INCLUDE_MERGES -> commitsHistory.addAll(analysisRepo.firstParentsCommits)
        }
        commitsHistory.reverse() // reverse history == from oldest commit to newest

        return if (commitsHistory.isEmpty()) {
            Status.EMPTY_HISTORY
        } else if (commitsHistory.size < config.minCommitsNumber) {
            Status.SMALL_COMMITS_NUMBER
        } else if (analysisRepo.mergesPart < config.mergesPart) {
            Status.SMALL_MERGES_PART
        } else {
            Status.LOADED
        }
    }
}
