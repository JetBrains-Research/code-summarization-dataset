package reposanalyzer.logic.summarizers

import astminer.common.model.Node
import astminer.common.model.Parser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import reposanalyzer.config.AnalysisConfig
import reposanalyzer.config.Language
import reposanalyzer.logic.AnalysisRepository
import reposanalyzer.logic.getFilesByLanguage
import reposanalyzer.methods.MethodSummaryStorage
import reposanalyzer.parsing.MethodParseProvider
import reposanalyzer.utils.WorkLogger
import reposanalyzer.utils.deleteDirectory
import reposanalyzer.utils.prettyDate
import reposanalyzer.zipper.Zipper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class NoHistorySummarizer(
    private val analysisPath: String? = null,
    private val analysisRepo: AnalysisRepository? = null,
    private val dumpPath: String,
    private val parsers: ConcurrentHashMap<Language, Parser<out Node>>,
    private val config: AnalysisConfig
) : Zipper, Summarizer {

    private companion object {
        const val REPO_INFO = "repo_info.json"
        const val WORK_LOG = "work_log.txt"
    }

    @Volatile
    override var status = SummarizerStatus.NOT_INITIALIZED

    private var analysisStart: Long = 0
    private var analysisEnd: Long = 0

    private lateinit var analysisDir: File
    private lateinit var methodParseProvider: MethodParseProvider
    private lateinit var summaryStorage: MethodSummaryStorage
    private lateinit var workLogger: WorkLogger

    override fun run() {
        analysisStart = System.currentTimeMillis()
        init()
        if (status != SummarizerStatus.LOADED) {
            workLogger.add("> SUMMARIZER NOT LOADED: $status")
            return
        }
        status = SummarizerStatus.RUNNING
        try {
            workLogger.add("> analysis started ${prettyDate(System.currentTimeMillis())}")
            processFiles()
            analysisEnd = System.currentTimeMillis()
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
            status = if (analysisRepo != null) {
                initRepo()
            } else if (analysisPath != null) {
                initDir()
            } else {
                SummarizerStatus.NO_PATHS_TO_ANALYSE
            }
        } catch (e: Exception) {
            workLogger.add("========= WORKER INIT ERROR: $analysisRepo =========")
            workLogger.add(e.stackTraceToString())
            status = SummarizerStatus.INIT_ERROR
        } finally {
            workLogger.dump()
        }
    }

    private fun initRepo(): SummarizerStatus =
        if (analysisRepo != null && analysisRepo.initRepository(dumpPath)) {
            analysisDir = File(analysisRepo.path)
            SummarizerStatus.LOADED
        } else {
            SummarizerStatus.REPO_NOT_PRESENT
        }

    private fun initDir() =
        if (analysisPath != null && File(analysisPath).isDirectory) {
            analysisDir = File(analysisPath)
            SummarizerStatus.LOADED
        } else {
            SummarizerStatus.BAD_DIR
        }

    private fun initStorageAndLogs() {
        File(dumpPath).mkdirs()
        val workLogPath = File(dumpPath).resolve(WORK_LOG).absolutePath
        workLogger = WorkLogger(workLogPath, isDebug = config.isDebugSummarizers)
        summaryStorage = MethodSummaryStorage(
            dumpPath, config.isAstDotFormat, config.isCode2SeqDump, config.summaryDumpThreshold, workLogger
        )
        methodParseProvider = MethodParseProvider(parsers, summaryStorage, config, analysisRepo)
    }

    private fun processFiles() = analysisDir.walkTopDown()
        .filter { !it.isHidden && !it.isDirectory }
        .toList()
        .getFilesByLanguage(config.languages)
        .parseFilesByLanguage()

    private fun Map<Language, List<File>>.parseFilesByLanguage() =
        this.filter { (_, files) -> files.isNotEmpty() }
            .forEach { (lang, files) ->
                if (!methodParseProvider.parse(files, lang, analysisDir.absolutePath)) {
                    workLogger.add("> unsupported language $lang -- no parser")
                }
            }

    private fun dumpSummary() {
        val secondsSpent = (analysisEnd - analysisStart) / 1000L
        val stats = summaryStorage.stats
        val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        val statsNode = stats.toJSON(mapper) as ObjectNode
        analysisRepo?.let {
            statsNode.set<JsonNode>("owner", mapper.valueToTree(it.owner))
            statsNode.set<JsonNode>("name", mapper.valueToTree(it.name))
        }
        statsNode.set<JsonNode>("analysis_languages", mapper.valueToTree(config.languages))
        statsNode.set<JsonNode>("process_end_status", mapper.valueToTree(status))
        statsNode.set<JsonNode>("seconds_spent", mapper.valueToTree(if (secondsSpent >= 0) secondsSpent else 0))
        mapper.writeValue(FileOutputStream(File(dumpPath).resolve(REPO_INFO), false), statsNode)
    }

    private fun dump() {
        try {
            dumpSummary()
            summaryStorage.dump()
            summaryStorage.dumpVisited()
            summaryStorage.clear()
            workLogger.add(
                "> TOTAL DUMPS [${summaryStorage.stats.totalMethods} methods, " +
                    "${summaryStorage.stats.pathsNumber} paths]"
            )
            workLogger.dump()
            analysisRepo?.git?.close()
            if (analysisRepo != null && status != SummarizerStatus.REPO_NOT_PRESENT) {
                if (config.removeRepoAfterAnalysis) {
                    analysisRepo.path.deleteDirectory()
                }
            }
            if (config.zipFiles && status == SummarizerStatus.DONE) {
                compressFolder(File(dumpPath), listOf(REPO_INFO, WORK_LOG), config.removeAfterZip)
            }
        } catch (e: Exception) {
            workLogger.add("========= WORKER DUMP ERROR: $analysisRepo =========")
            workLogger.add(e.stackTraceToString())
            workLogger.dump()
        }
    }

    @Override
    override fun toString(): String {
        if (analysisRepo?.owner != null && analysisRepo.name != null) {
            return "/${analysisRepo.owner}/${analysisRepo.name} ${analysisDir.absolutePath}"
        }
        return analysisDir.absolutePath
    }
}
