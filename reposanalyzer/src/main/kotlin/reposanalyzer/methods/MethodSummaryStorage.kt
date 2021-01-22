package reposanalyzer.methods

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import reposanalyzer.utils.WorkLogger
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
    private val dumpFilePath: String,
    private val dumpThreshold: Int = 500,
    private val logger: WorkLogger? = null
) {
    private val data = mutableSetOf<MethodSummary>()
    private val visited = mutableSetOf<MethodIdentity>()
    private val dumpFile = File(dumpFilePath)
    private val objectMapper = jacksonObjectMapper()

    var methodsNumber: Int = 0

    val size: Int
        get() = data.size

    init {
        dumpFile.createNewFile()
        clearFile()
    }

    fun add(summary: MethodSummary): Boolean {
        if (contains(summary)) {
            return false
        }
        visited.add(MethodIdentity(summary.fullName, summary.filePath))
        data.add(summary)
        methodsNumber++
        if (size >= dumpThreshold) {
            dump()
        }
        return true
    }

    fun dump() {
        FileOutputStream(dumpFile, true).bufferedWriter().use { writer ->
            toJSON(objectMapper).forEach { jsonNode ->
                val string = jsonNode.toString()
                writer.appendLine(string)
            }
        }
        logger?.add("> ${data.size} methods was dumped")
        data.clear() // clear data after dump WITHOUT cleaning visited list
    }

    fun getStats() = MethodSummaryStorageStats(visited)

    fun clear() {
        data.clear()
        visited.clear()
    }

    fun toJSON(objectMapper: ObjectMapper? = null): List<JsonNode> {
        val mapper = objectMapper ?: jacksonObjectMapper()
        return data.map { it.toJSON(mapper) }.toList()
    }

    fun contains(summary: MethodSummary): Boolean =
        contains(summary.fullName, summary.filePath)

    fun contains(normalizedFullName: String, filePath: String): Boolean =
        visited.contains(MethodIdentity(normalizedFullName, filePath))

    fun notContains(summary: MethodSummary): Boolean = !contains(summary)

    fun notContains(normalizedFullName: String, filePath: String): Boolean = !contains(normalizedFullName, filePath)

    private fun clearFile() = FileOutputStream(dumpFile, false).bufferedWriter()
}
