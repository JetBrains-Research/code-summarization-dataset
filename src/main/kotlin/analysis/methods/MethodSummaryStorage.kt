package analysis.methods

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import analysis.config.IdentityConfig
import analysis.utils.WorkLogger
import analysis.utils.clearFile
import java.io.File
import java.io.FileOutputStream

/*
 *  Summary storage with line-delimited ('\n') JSON dumps:
 *      1. after dumpThreshold
 *      2. by explicit dumpData method call
 *
 *  Uniqueness checks through:
 *      [method normalized full name, types of arguments, method return type]
 *
 *  Visited methods storage (visited) does not clear at dumps
 */
class MethodSummaryStorage(
    private val identityConfig: IdentityConfig,
    private val dumpFolder: String,
    private val isAstDumpDotFormat: Boolean,
    private val isCode2SecDump: Boolean,
    private val dumpThreshold: Int = 200,
    private val logger: WorkLogger? = null
) {
    private companion object {
        const val METHODS_SUMMARY_FILE = "methods.jsonl"
        const val METHODS_PATHS_JSONL_FILE = "paths.jsonl"
        const val METHODS_PATHS_C2S_FILE = "paths.c2s"
        const val METHODS_VISITED_FILE = "visited.jsonl"
        const val PATHS_DUMP_THRESHOLD = 20_000
    }

    private enum class DumpType {
        MAIN,
        PATHS_JSON
    }

    private val data = mutableSetOf<MethodSummary>()
    private val visited = mutableSetOf<MethodIdentity>()
    private val summaryDumpFile = File(dumpFolder).resolve(METHODS_SUMMARY_FILE)
    private val pathsJSONDumpFile = File(dumpFolder).resolve(METHODS_PATHS_JSONL_FILE)
    private val pathsC2SDumpFile = File(dumpFolder).resolve(METHODS_PATHS_C2S_FILE)
    private val identityDumpFile = File(dumpFolder).resolve(METHODS_VISITED_FILE)
    private val objectMapper = jacksonObjectMapper()

    val stats = MethodSummaryStorageStats()

    val size: Int
        get() = data.size

    init {
        stats.identityConfig = identityConfig
        summaryDumpFile.createNewFile()
        pathsJSONDumpFile.createNewFile()
        identityDumpFile.createNewFile()
        summaryDumpFile.clearFile()
        pathsJSONDumpFile.clearFile()
        identityDumpFile.clearFile()
        if (isCode2SecDump) {
            pathsC2SDumpFile.createNewFile()
            pathsC2SDumpFile.clearFile()
        }
    }

    fun add(summary: MethodSummary): Boolean {
        if (contains(summary)) return false
        stats.registerMethod(summary)
        summary.id = stats.totalMethods
        visited.add(MethodIdentity.create(summary, identityConfig))
        data.add(summary)
        if (readyToDump()) dump()
        return true
    }

    fun dump() {
        try {
            dumpJSONFile(summaryDumpFile, DumpType.MAIN)
            dumpJSONFile(pathsJSONDumpFile, DumpType.PATHS_JSON)
            dumpC2SFile(pathsC2SDumpFile)
        } catch (e: Exception) {
            logger?.add("DUMP ERROR -- SUMMARY STORAGE")
            logger?.add(e.toString())
        } finally {
            logger?.add("> dumped [${data.size} methods, ${data.map { it.paths.size }.sum()} paths]")
            data.clear() // clear data after dump WITHOUT cleaning visited list
        }
    }

    fun dumpVisited() = FileOutputStream(identityDumpFile).bufferedWriter().use { writer ->
        visited.forEach {
            writer.appendLine(it.toJSON(objectMapper).toString())
        }
    }

    private fun dumpJSONFile(file: File, dumpType: DumpType) =
        FileOutputStream(file, true).bufferedWriter().use { writer ->
            data.forEach { summary ->
                val node = when (dumpType) {
                    DumpType.MAIN -> summary.toJSONMain(objectMapper, isAstDumpDotFormat)
                    DumpType.PATHS_JSON -> summary.toJSONPaths(objectMapper)
                }
                val string = node.toString()
                writer.appendLine(string)
            }
        }

    private fun dumpC2SFile(file: File) {
        if (!isCode2SecDump) return
        FileOutputStream(file, true).bufferedWriter().use { writer ->
            data.forEach { summary ->
                val string = summary.toC2SPaths() ?: return@forEach
                writer.appendLine(string)
            }
        }
    }

    private fun readyToDump(): Boolean =
        size >= dumpThreshold || data.map { it.paths.size }.sum() >= PATHS_DUMP_THRESHOLD

    fun clear() {
        data.clear()
        visited.clear()
        stats.clear()
    }

    fun contains(summary: MethodSummary): Boolean = if (identityConfig.isNoIdentity) false
    else visited.contains(MethodIdentity.create(summary = summary, config = identityConfig))

    fun contains(realId: MethodIdentity): Boolean = if (identityConfig.isNoIdentity) false
    else visited.contains(MethodIdentity.create(realId = realId, config = identityConfig))
}
