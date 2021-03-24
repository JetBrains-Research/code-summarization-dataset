package reposanalyzer.logic.summarizers

import astminer.common.model.Node
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
import reposanalyzer.logic.AnalysisRepository
import reposanalyzer.logic.getFilesByLanguage
import reposanalyzer.logic.getSupportedFiles
import reposanalyzer.methods.MethodSummaryStorage
import reposanalyzer.parsing.MethodParseProvider
import reposanalyzer.parsing.SafeParser
import reposanalyzer.utils.CommitsLogger
import reposanalyzer.utils.WorkLogger
import reposanalyzer.utils.deleteDirectory
import reposanalyzer.utils.getAbsolutePatches
import reposanalyzer.utils.getNotHiddenNotDirectoryFiles
import reposanalyzer.utils.prettyDate
import reposanalyzer.utils.removePrefixPath
import reposanalyzer.zipper.Zipper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class HistorySummarizer(
    private val analysisRepo: AnalysisRepository,
    private val dumpPath: String,
    private val parsers: ConcurrentHashMap<Language, SafeParser<out Node>>,
    private val config: AnalysisConfig
) : Zipper, Summarizer {

    private companion object {
        const val REPO_INFO = "repo_info.json"
        const val COMMITS_LOG = "commits_log.jsonl"
        const val WORK_LOG = "work_log.txt"
        const val FIRST_HASH = 7
    }

    @Volatile
    override var status = SummarizerStatus.NOT_INITIALIZED

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
        if (status != SummarizerStatus.LOADED) {
            workLogger.add("> SUMMARIZER NOT LOADED: $status")
            return
        }
        status = SummarizerStatus.RUNNING
        try {
            workLogger.add("> analysis started ${prettyDate(System.currentTimeMillis())}")
            processCommits()
            analysisEnd = System.currentTimeMillis()
            git.checkoutHashOrName(defaultBranchHead?.name) // back to normal head
            workLogger.add("> back to start HEAD: ${defaultBranchHead?.name}")
            workLogger.add("> analysis ended ${prettyDate(System.currentTimeMillis())}")
            if (status == SummarizerStatus.RUNNING) {
                status = SummarizerStatus.DONE
            }
        } catch (e: Exception) {
            workLogger.add("========= WORKER RUNNING ERROR FOR $analysisRepo =========")
            workLogger.add(e.stackTraceToString())
            status = SummarizerStatus.WORK_ERROR
        } finally {
            dump()
            workLogger.dump()
        }
    }

    private fun init() {
        try {
            initStorageAndLogs()
            status = if (!analysisRepo.initRepository(dumpPath)) {
                SummarizerStatus.REPO_NOT_PRESENT
            } else if (!analysisRepo.loadDefaultBranchHead()) {
                SummarizerStatus.INIT_BAD_DEF_BRANCH_ERROR
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
                SummarizerStatus.INIT_BAD_DEF_BRANCH_ERROR -> workLogger.add("> BAD DEFAULT BRANCH: $analysisRepo")
                SummarizerStatus.EMPTY_HISTORY -> workLogger.add("> NO HISTORY FOR REPOSITORY: $analysisRepo")
                SummarizerStatus.LOADED -> workLogger.add("> SUCCESSFUL LOADED: $analysisRepo")
                else -> {} // ignore
            }
        } catch (e: Exception) {
            workLogger.add("========= WORKER INIT ERROR: $analysisRepo =========")
            workLogger.add(e.stackTraceToString())
            status = SummarizerStatus.INIT_ERROR
        } finally {
            workLogger.dump()
        }
    }

    private fun initStorageAndLogs() {
        File(dumpPath).mkdirs()
        val workLogPath = File(dumpPath).resolve(WORK_LOG).absolutePath
        val commitsLogPath = File(dumpPath).resolve(COMMITS_LOG).absolutePath
        workLogger = WorkLogger(workLogPath, isDebug = config.isDebugSummarizers)
        commitsLogger = CommitsLogger(commitsLogPath, config.logDumpThreshold)
        summaryStorage = MethodSummaryStorage(
            config.identityConfig,
            dumpPath,
            config.isAstDotFormat,
            config.isCode2SeqDump,
            config.summaryDumpThreshold,
            workLogger
        )
        methodParseProvider = MethodParseProvider(parsers, summaryStorage, config, analysisRepo)
    }

    private fun processCommits() {
        currCommit = commitsHistory.firstOrNull() ?: return // log must be not empty by init state
        currCommit?.processCommit(currCommit, analysisRepo.path, filesPaths = listOf()) // process first commit
        for (i in 1 until commitsHistory.size) { // process others commits
            if (status != SummarizerStatus.RUNNING) break
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
            .forEach { (lang, files) ->
                if (!methodParseProvider.parse(files, lang, analysisRepo.path, currCommit)) {
                    workLogger.add("> unsupported language $lang -- no parser")
                }
            }

    private fun dumpRepoSummary() {
        val secondsSpent = (analysisEnd - analysisStart) / 1000L
        val stats = summaryStorage.stats
        val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        val repoNode = analysisRepo.toJSON(mapper) as ObjectNode
        repoNode.set<JsonNode>("processed_commits", mapper.valueToTree(commitsHistory.size))
        val statsNode = stats.toJSON(mapper)
        val merged = mapper.readerForUpdating(repoNode)
            .readValue<JsonNode>(statsNode) as ObjectNode
        merged.set<JsonNode>("analysis_languages", mapper.valueToTree(config.languages))
        merged.set<JsonNode>("process_end_status", mapper.valueToTree(status))
        merged.set<JsonNode>("seconds_spent", mapper.valueToTree(if (secondsSpent >= 0) secondsSpent else 0))
        mapper.writeValue(FileOutputStream(File(dumpPath).resolve(REPO_INFO), false), merged)
    }

    private fun dump() {
        try {
            dumpRepoSummary()
            summaryStorage.dump()
            summaryStorage.dumpVisited()
            summaryStorage.clear()
            workLogger.add(
                "> TOTAL DUMPS [${summaryStorage.stats.totalMethods} methods, " +
                    "${summaryStorage.stats.pathsNumber} paths]"
            )
            commitsLogger.dump()
            commitsLogger.clear()
            analysisRepo.clear()
            if (status != SummarizerStatus.REPO_NOT_PRESENT) {
                if (config.removeRepoAfterAnalysis) {
                    analysisRepo.path.deleteDirectory()
                }
                if (config.zipFiles) {
                    compressFolder(File(dumpPath), listOf(REPO_INFO, WORK_LOG), config.removeAfterZip)
                }
            }
        } catch (e: Exception) {
            workLogger.add("========= WORKER DUMP ERROR: $analysisRepo =========")
            workLogger.add(e.stackTraceToString())
            workLogger.dump()
        }
    }

    private fun loadHistory(): SummarizerStatus {
        analysisRepo.loadCommitsHistory()
        when (config.commitsType) {
            CommitsType.ONLY_MERGES -> commitsHistory.addAll(analysisRepo.mergeHistory.history)
            CommitsType.FIRST_PARENTS_INCLUDE_MERGES -> commitsHistory.addAll(analysisRepo.firstParentsCommits)
        }
        commitsHistory.reverse() // reverse history == from oldest commit to newest

        return if (commitsHistory.isEmpty()) {
            SummarizerStatus.EMPTY_HISTORY
        } else if (commitsHistory.size < config.minCommitsNumber) {
            SummarizerStatus.SMALL_COMMITS_NUMBER
        } else if (analysisRepo.mergesPart < config.mergesPart) {
            SummarizerStatus.SMALL_MERGES_PART
        } else {
            SummarizerStatus.LOADED
        }
    }

    override fun toString(): String {
        if (analysisRepo.owner != null && analysisRepo.name != null) {
            return "/${analysisRepo.owner}/${analysisRepo.name}"
        }
        return analysisRepo.path
    }
}
