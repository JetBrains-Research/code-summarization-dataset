package reposanalyzer.methods

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import reposanalyzer.utils.WorkLogger
import reposanalyzer.utils.clearFile
import java.io.File
import java.io.FileOutputStream

/*
 *  Summary storage with line-delimited ('\n') JSON dumps:
 *      1. after dumpThreshold
 *      2. by explicit dumpData method call
 *
 *  Uniqueness checks through pair:
 *      [method normalized full name, filepath to file with method]
 *
 *  Visited methods storage (visited) does not clear at dumps
 */
data class MethodIdentity(
    val methodNormalizedFullName: String,
    val filePath: String
)

class MethodSummaryStorage(
    private val summaryDumpPath: String,
    private val pathsDumpPath: String,
    private val dumpThreshold: Int = 200,
    private val logger: WorkLogger? = null
) {
    private companion object {
        const val PATHS_DUMP_THRESHOLD = 10_000
    }

    private val data = mutableSetOf<MethodSummary>()
    private val visited = mutableSetOf<MethodIdentity>()
    private val summaryDumpFile = File(summaryDumpPath)
    private val pathsDumpFile = File(pathsDumpPath)
    private val objectMapper = jacksonObjectMapper()

    var methodsNumber: Int = 0
    var pathsNumber: Int = 0

    val size: Int
        get() = data.size

    init {
        summaryDumpFile.createNewFile()
        pathsDumpFile.createNewFile()
        summaryDumpFile.absolutePath.clearFile()
        pathsDumpFile.absolutePath.clearFile()
    }

    fun add(summary: MethodSummary): Boolean {
        if (contains(summary)) return false
        visited.add(MethodIdentity(summary.fullName, summary.filePath))
        data.add(summary)
        summary.id = ++methodsNumber
        pathsNumber += summary.paths.size
        if (readyToDump()) dump()
        return true
    }

    fun dump() {
        dumpToFile(summaryDumpFile, isMain = true)
        dumpToFile(pathsDumpFile, isMain = false)
        logger?.add("> dumped [${data.size} methods, ${data.map { it.paths.size }.sum()} paths]")
        data.clear() // clear data after dump WITHOUT cleaning visited list
    }

    private fun dumpToFile(file: File, isMain: Boolean = true) =
        FileOutputStream(file, true).bufferedWriter().use { writer ->
            data.forEach { summary ->
                val node = if (isMain) summary.toJSONMain(objectMapper) else summary.toJSONPaths(objectMapper)
                val string = node.toString()
                writer.appendLine(string)
            }
        }

    private fun readyToDump(): Boolean =
        size >= dumpThreshold || data.map { it.paths.size }.sum() >= PATHS_DUMP_THRESHOLD

    fun clear() {
        data.clear()
        visited.clear()
    }

    fun getStats() = MethodSummaryStorageStats(visited, pathsNumber)

    fun toJSON(objectMapper: ObjectMapper? = null): List<JsonNode> {
        val mapper = objectMapper ?: jacksonObjectMapper()
        return data.map { it.toJSONMain(mapper) }.toList()
    }

    fun contains(summary: MethodSummary): Boolean =
        contains(summary.fullName, summary.filePath)

    fun contains(normalizedFullName: String, filePath: String): Boolean =
        visited.contains(MethodIdentity(normalizedFullName, filePath))

    fun notContains(summary: MethodSummary): Boolean = !contains(summary)

    fun notContains(normalizedFullName: String, filePath: String): Boolean = !contains(normalizedFullName, filePath)
}
