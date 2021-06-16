package analysis.granularity.method

import analysis.config.IdentityConfig
import analysis.granularity.Summary
import analysis.granularity.SummaryStorage
import analysis.utils.createAndClear
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import utils.FileLogger
import java.io.File
import java.io.FileOutputStream

class MethodSummaryStorage(
    private val dumpFolder: String,
    private val identityConfig: IdentityConfig,
    private val logger: FileLogger? = null,
    private val isCode2SeqDump: Boolean = false,
    private val isAstDotFormat: Boolean = false,
    private val summaryDumpThreshold: Int = 200
) : SummaryStorage {

    private companion object {
        const val METHODS_SUMMARY_JSL = "methods.jsonl"
        const val METHODS_PATHS_JSL = "paths.jsonl"
        const val METHODS_PATHS_C2S = "paths.c2s"
        const val METHODS_IDENTITIES = "identities.jsonl"
        const val PATHS_DUMP_THRESHOLD = 100_000
    }

    private enum class DumpType {
        METHODS_JSL,
        PATHS_JSL,
        IDS_JSL
    }

    private val data = mutableListOf<MethodSummary>() // methods summaries buffer
    private val visited = mutableSetOf<MethodIdentity>() // methods visited list by uniqueness tuple
    private val identities = mutableListOf<MethodIdentity>() // methods identities buffer
    private val stats = MethodSummaryStorageStats(identityConfig)

    val size: Int
        get() = data.size

    private val summaryJSLDumpFile = File(dumpFolder).resolve(METHODS_SUMMARY_JSL)
    private val pathsJSLDumpFile = File(dumpFolder).resolve(METHODS_PATHS_JSL)
    private val pathsC2SDumpFile = File(dumpFolder).resolve(METHODS_PATHS_C2S)
    private val identitiesJSLDumpFile = File(dumpFolder).resolve(METHODS_IDENTITIES)

    private val objectMapper = jacksonObjectMapper()

    init {
        summaryJSLDumpFile.createAndClear()
        pathsJSLDumpFile.createAndClear()
        identitiesJSLDumpFile.createAndClear()
        if (isCode2SeqDump) {
            pathsC2SDumpFile.createAndClear()
        }
    }

    override fun add(summary: Summary): Boolean {
        summary as MethodSummary
        val configIdentity = MethodIdentity.configIdentity(summary, identityConfig)
        if (contains(configIdentity)) return false
        stats.registerMethod(summary)
        visited.add(configIdentity)
        identities.add(MethodIdentity.fullIdentity(summary))
        data.add(summary)
        if (readyToDump()) dump()
        return true
    }

    override fun dump() {
        dumpJSONLines(summaryJSLDumpFile, DumpType.METHODS_JSL)
        dumpJSONLines(identitiesJSLDumpFile, DumpType.IDS_JSL)
        dumpJSONLines(pathsJSLDumpFile, DumpType.PATHS_JSL)
        if (isCode2SeqDump) {
            dumpPathsC2S(pathsC2SDumpFile)
        }
        if (data.size > 0) {
            logger?.add("dumped [methods: ${data.size}, paths: ${data.sumOf { it.paths.size }}]")
        }
        clearAfterDump() // clears data after dump WITHOUT cleaning visited list
    }

    override fun clear() {
        data.clear()
        identities.clear()
        visited.clear()
        stats.clear()
    }

    override fun stats(): JsonNode = stats.toJSON(objectMapper)

    private fun dumpJSONLines(file: File, type: DumpType) =
        FileOutputStream(file, true).bufferedWriter().use { w ->
            when (type) {
                DumpType.METHODS_JSL -> data.forEach { summary ->
                    w.appendLine(summary.toJSONMethod(objectMapper, isAstDotFormat).toString())
                }
                DumpType.PATHS_JSL -> data.forEach { summary ->
                    w.appendLine(summary.toJSONPaths(objectMapper).toString())
                }
                DumpType.IDS_JSL -> identities.forEach { id ->
                    w.appendLine(id.toJSON(objectMapper).toString())
                }
            }
        }

    private fun dumpPathsC2S(file: File) = FileOutputStream(file, true).bufferedWriter().use { writer ->
        data.forEach { summary ->
            val string = summary.toC2SPaths() ?: return@forEach
            writer.appendLine(string)
        }
    }

    private fun readyToDump(): Boolean =
        size >= summaryDumpThreshold || data.sumOf { it.paths.size } >= PATHS_DUMP_THRESHOLD

    private fun clearAfterDump() {
        data.clear()
        identities.clear()
    }

    fun contains(summary: MethodSummary): Boolean = if (identityConfig.isNoIdentity) false
    else visited.contains(MethodIdentity.configIdentity(summary, identityConfig))

    fun contains(fullId: MethodIdentity): Boolean = if (identityConfig.isNoIdentity) false
    else visited.contains(MethodIdentity.configIdentity(fullId, identityConfig))
}
