package analysis.logic.summarizers

import analysis.config.AnalysisConfig
import analysis.config.enums.CommitsType
import analysis.git.AnalysisRepository
import analysis.git.checkoutCommit
import analysis.git.checkoutHashOrName
import analysis.git.getCommitsDiff
import analysis.git.getDiffFiles
import analysis.granularity.ParseProvider
import analysis.granularity.ParseResult
import analysis.granularity.SummaryStorage
import analysis.logic.CommonInfo
import analysis.logic.ParseEnvironment
import analysis.logic.ReadyInfo
import analysis.logic.WorkEnvironment
import analysis.logic.getFilesByLanguage
import analysis.logic.getSupportedFiles
import analysis.logic.summarizers.utils.CommitsLogger
import analysis.logic.summarizers.utils.Zipper
import analysis.utils.deleteDirectory
import analysis.utils.getAbsolutePatches
import analysis.utils.getNotHiddenNotDirectoryFiles
import analysis.utils.prettyCurrentDate
import analysis.utils.removePrefixPath
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.eclipse.jgit.revwalk.RevCommit
import utils.FileLogger
import java.io.File
import java.io.FileOutputStream

class HistorySummarizer(
    private val id: Int,
    private val repo: AnalysisRepository,
    private val dumpFolder: String,
    private val config: AnalysisConfig,
    private val workEnv: WorkEnvironment,
    private val parseEnv: ParseEnvironment
) : Summarizer, Zipper {

    private companion object {
        const val INFO = "info.json"
        const val COMMITS_LOG = "commits_log.jsonl"
        const val WORK_LOG = "work_log.txt"
        const val FIRST_HASH = 7
    }

    @Volatile override var status = SummarizerStatus.NOT_INITIALIZED

    override val type = Summarizer.Type.HIST

    private lateinit var parseProvider: ParseProvider
    private lateinit var summaryStorage: SummaryStorage

    private var currCommit: RevCommit? = null
    private var prevCommit: RevCommit? = null
    private val commitsHistory = mutableListOf<RevCommit>()

    private var workLogger: FileLogger? = null
    private var commitsLogger: CommitsLogger? = null

    private var analysisStart: Long = 0
    private var analysisEnd: Long = 0

    override fun run() {
        try {
            if (!init()) {
                return
            }
            processCommits()
        } catch (e: Exception) {
            logWithStatusAndException(e, SummarizerStatus.WORK_ERROR)
        } finally {
            try {
                dump()
                dumpInfo()
            } catch (e: Exception) {
                logWithStatusAndException(e, SummarizerStatus.DUMP_EXCEPTION)
            }
            workEnv.registerReady(
                ReadyInfo(id, status, type, "${prettyCurrentDate()} ${repo.toStringNotNull()} ")
            )
        }
    }

    private fun init(): Boolean {
        if (!repo.initRepository(dumpFolder)) {
            status = SummarizerStatus.REPO_NOT_PRESENT
            workEnv.addMessage("$type WORKER $id -- repo doesn't exist / wasn't cloned: $repo")
            return false
        }
        initStorageAndLogs()
        if (!repo.loadDefaultBranchHead()) {
            status = SummarizerStatus.INIT_BAD_DEF_BRANCH_ERROR
            workLogger?.add("bad reference to head: $repo")
            return false
        }
        return loadHistory()
    }

    private fun initStorageAndLogs() {
        val workLogPath = File(dumpFolder).resolve(WORK_LOG).absolutePath
        val commitsLogPath = File(dumpFolder).resolve(COMMITS_LOG).absolutePath
        workLogger = FileLogger(workLogPath, config.isDebugWorkers, workEnv.mainLogger)
        commitsLogger = CommitsLogger(commitsLogPath, config.logDumpThreshold)
        parseProvider = ParseProvider.get(config, parseEnv)
        summaryStorage = SummaryStorage.get(dumpFolder, config, workLogger)
    }

    private fun loadHistory(): Boolean {
        repo.loadCommitsHistory()
        when (config.commitsType) {
            CommitsType.ONLY_MERGES -> commitsHistory.addAll(repo.mergeHistory.history)
            CommitsType.FIRST_PARENTS_INCLUDE_MERGES -> commitsHistory.addAll(repo.firstParentsCommits)
        }
        commitsHistory.reverse() // reverse history == from oldest commit to newest
        status = when {
            commitsHistory.isEmpty() -> SummarizerStatus.EMPTY_HISTORY
            commitsHistory.size < config.minCommitsNumber -> SummarizerStatus.SMALL_COMMITS_NUMBER
            repo.mergesPart < config.mergesPart -> SummarizerStatus.SMALL_MERGES_PART
            else -> SummarizerStatus.LOADED
        }
        if (status != SummarizerStatus.LOADED) {
            workLogger?.add("bad history: $status")
            return false
        }
        workLogger?.add("default repo branch: ${repo.defaultBranchHead?.name}")
        workLogger?.add(
            "commits count [merge: ${repo.mergeCommitsNumber}, first-parents: ${repo.firstParentsCommitsNumber}]"
        )
        return true
    }

    private fun processCommits() {
        try {
            status = SummarizerStatus.RUNNING
            analysisStart = System.currentTimeMillis()
            currCommit = commitsHistory.firstOrNull() ?: return // log must be not empty by init state
            currCommit?.processCommit(currCommit, repo.path, filesPaths = listOf()) // process first commit
            for (i in 1 until commitsHistory.size) { // process others commits
                if (status != SummarizerStatus.RUNNING) break
                prevCommit = currCommit
                currCommit = commitsHistory[i]
                val diff = repo.git.getCommitsDiff(repo.repository.newObjectReader(), currCommit, prevCommit)
                val processedDiff = diff.getDiffFiles(repo.repository, config.copyDetection)
                val supportedFiles = processedDiff.getSupportedFiles(config.supportedFileExtensions)
                val filesPaths = supportedFiles.getAbsolutePatches(repo.path) // supported extensions files
                workLogger?.add(
                    "files diff [${prevCommit?.name?.substring(0, FIRST_HASH)}, " +
                        "${currCommit?.name?.substring(0, FIRST_HASH)}, " +
                        "total: ${diff.size}, supported: ${filesPaths.size}]"
                )
                if (filesPaths.isEmpty()) {
                    commitsLogger?.add(currCommit, prevCommit, mapOf()) // no files to parse => no checkout
                } else {
                    currCommit?.processCommit(prevCommit, filesPaths = filesPaths)
                }
            }
            status = SummarizerStatus.DONE
            repo.defaultBranchHead?.name?.let { repo.git.checkoutHashOrName(it) } // back to normal head
        } finally {
            analysisEnd = System.currentTimeMillis()
        }
    }

    private fun RevCommit.processCommit(
        prevCommit: RevCommit? = null,
        dirPath: String? = null,
        filesPaths: List<String> = listOf()
    ) {
        workLogger?.add("checkout on [${this.name.substring(0, FIRST_HASH)}, ${this.shortMessage}]")
        repo.git.checkoutCommit(this) // checkout
        val files = if (dirPath != null) {
            getNotHiddenNotDirectoryFiles(repo.path)
        } else {
            getNotHiddenNotDirectoryFiles(filesPaths)
        }
        val filesByLang = files.getFilesByLanguage(config.languages).filter { (_, files) -> files.isNotEmpty() }
        filesByLang.forEach { (lang, files) ->
            val parseResults = parseProvider.parse(files, config.parser, lang)
            // exceptions
            parseResults.filter { it.exception != null }.forEach { it.logExceptionInParseResult() }
            // results
            parseProvider.processParseResults(
                parseResults.filter { it.exception == null && it.result.isNotEmpty() },
                summaryStorage,
                lang,
                CommonInfo(repo.path, repo, currCommit)
            )
        }
        commitsLogger?.add(this, prevCommit, filesByLang.removePrefixPath(repo.path + File.separator))
    }

    private fun dump() {
        summaryStorage.dump()
        summaryStorage.clear()
        commitsLogger?.dump()
        commitsLogger?.clear()
        workLogger?.dump()
        repo.git.close()
        if (config.removeRepoAfterAnalysis) {
            repo.path.deleteDirectory()
        }
        if (config.zipFiles) {
            compressFolder(File(dumpFolder), listOf(INFO, WORK_LOG), config.removeAfterZip)
        }
    }

    private fun dumpInfo() {
        val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        val statsNode = summaryStorage.stats() as ObjectNode
        val repoNode = repo.toJSON(mapper) as ObjectNode
        repoNode.set<JsonNode>("processed_commits", mapper.valueToTree(commitsHistory.size))
        val merged = mapper.readerForUpdating(repoNode)
            .readValue<JsonNode>(statsNode) as ObjectNode
        merged.set<JsonNode>("type", mapper.valueToTree(type.label))
        merged.set<JsonNode>("source", mapper.valueToTree("/${repo.owner}/${repo.name}"))
        merged.set<JsonNode>("analysis_languages", mapper.valueToTree(config.languages))
        merged.set<JsonNode>("process_end_status", mapper.valueToTree(status))
        merged.set<JsonNode>("seconds_spent", mapper.valueToTree((analysisEnd - analysisStart) / 1000L))
        mapper.writeValue(FileOutputStream(File(dumpFolder).resolve(INFO), false), merged)
    }

    private fun logWithStatusAndException(exception: Exception, status: SummarizerStatus) {
        workEnv.addMessage("========= DEAD $type WORKER $id $status exception for $repo =========")
        workEnv.addMessage(exception.stackTraceToString())
        this.status = status
    }

    private fun ParseResult.logExceptionInParseResult() = exception?.let {
        workLogger?.add("parse exception for file: $filePath")
        workLogger?.add(it.stackTraceToString())
    }
}
