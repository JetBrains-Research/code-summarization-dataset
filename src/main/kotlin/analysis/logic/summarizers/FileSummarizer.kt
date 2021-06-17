package analysis.logic.summarizers

import analysis.config.AnalysisConfig
import analysis.granularity.ParseProvider
import analysis.granularity.ParseResult
import analysis.granularity.SummaryStorage
import analysis.logic.CommonInfo
import analysis.logic.ParseEnvironment
import analysis.logic.ReadyInfo
import analysis.logic.WorkEnvironment
import analysis.logic.getSupportedFiles
import analysis.logic.summarizers.utils.Zipper
import analysis.utils.deleteDirectory
import analysis.utils.prettyCurrentDate
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import utils.FileLogger
import java.io.File
import java.io.FileOutputStream

class FileSummarizer(
    private val id: Int,
    private val dataPath: String,
    private val dumpFolder: String,
    private val config: AnalysisConfig,
    private val workEnv: WorkEnvironment,
    private val parseEnv: ParseEnvironment,
) : Summarizer, Zipper {

    private companion object {
        const val INFO = "info.json"
        const val WORK_LOG = "work_log.txt"
    }

    @Volatile override var status = SummarizerStatus.NOT_INITIALIZED

    override val type = if (File(dataPath).isDirectory) Summarizer.Type.DIR else Summarizer.Type.FILE

    private lateinit var parseProvider: ParseProvider
    private lateinit var summaryStorage: SummaryStorage

    private var workLogger: FileLogger? = null

    private var analysisStart: Long = 0
    private var analysisEnd: Long = 0

    override fun run() {
        try {
            if (!init()) {
                return
            }
            processFiles()
        } catch (e: Exception) {
            logWithStatusAndException(e, SummarizerStatus.WORK_ERROR)
        } finally {
            try {
                dump()
                dumpInfo()
                end()
            } catch (e: Exception) {
                logWithStatusAndException(e, SummarizerStatus.DUMP_EXCEPTION)
            }
            workEnv.registerReady(
                ReadyInfo(id, status, type, "${prettyCurrentDate()} $dataPath")
            )
        }
    }

    private fun init(): Boolean {
        if (!File(dataPath).exists()) {
            status = SummarizerStatus.BAD_DIR
            workEnv.addMessage("$type WORKER $id -- path doesn't exist: $dataPath")
            return false
        }
        val workLogPath = File(dumpFolder).resolve(WORK_LOG).absolutePath
        workLogger = FileLogger(workLogPath, config.isDebugWorkers, workEnv.mainLogger)
        parseProvider = ParseProvider.get(config, parseEnv)
        summaryStorage = SummaryStorage.get(dumpFolder, config, workLogger)
        status = SummarizerStatus.LOADED
        workLogger?.add("$type WORKER $id LOADED ${prettyCurrentDate()} $dataPath")
        return true
    }

    private fun processFiles() {
        try {
            status = SummarizerStatus.RUNNING
            analysisStart = System.currentTimeMillis()
            val filesByLang = File(dataPath).getSupportedFiles(config.languages)
            filesByLang.forEach { (lang, files) ->
                val parseResults = parseProvider.parse(files, config.parser, lang)
                // exceptions
                parseResults.filter { it.exception != null }.forEach { it.logException(workLogger) }
                // results
                parseProvider.processParseResults(
                    parseResults.filter { it.exception == null },
                    summaryStorage,
                    lang,
                    CommonInfo(dataPath)
                )
            }
            status = SummarizerStatus.DONE
        } finally {
            analysisEnd = System.currentTimeMillis()
        }
    }

    private fun dump() {
        summaryStorage.dump()
        workLogger?.dump()
    }

    private fun dumpInfo() {
        val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        val infoNode = mapper.createObjectNode()
        infoNode.set<JsonNode>("type", mapper.valueToTree(type.label))
        infoNode.set<JsonNode>("source", mapper.valueToTree(dataPath))
        infoNode.set<JsonNode>("process_end_status", mapper.valueToTree(status))
        infoNode.set<JsonNode>("seconds_spent", mapper.valueToTree((analysisEnd - analysisStart) / 1000L))
        val statsNode = summaryStorage.stats() as ObjectNode
        val merged = mapper.readerForUpdating(infoNode)
            .readValue<JsonNode>(statsNode) as ObjectNode
        mapper.writeValue(FileOutputStream(File(dumpFolder).resolve(INFO), false), merged)
    }

    private fun end() {
        summaryStorage.clear()
        if (config.zipFiles) {
            compressFolder(File(dumpFolder), listOf(INFO, WORK_LOG), config.removeAfterZip)
        }
    }

    private fun logWithStatusAndException(exception: Exception, status: SummarizerStatus) {
        workEnv.addMessage("========= DEAD $type WORKER $id $status exception for $dataPath =========")
        workEnv.addMessage(exception.stackTraceToString())
        this.status = status
    }
}
