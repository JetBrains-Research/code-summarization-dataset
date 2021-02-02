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
 *  Uniqueness checks through:
 *      [filepath to file with method, method normalized full name, types of arguments, method return type]
 *
 *  Visited methods storage (visited) does not clear at dumps
 */
data class MethodIdentity(
    val filePath: String,
    val methodNormalizedFullName: String,
    val methodArgsTypes: List<String> = listOf(),
    val methodReturnType: String? = null
) {
    fun toJSON(objectMapper: ObjectMapper? = null): JsonNode {
        val mapper = getObjectMapper(objectMapper)
        val jsonNode = mapper.createObjectNode()
        jsonNode.set<JsonNode>("return_type", mapper.valueToTree(methodReturnType))
        jsonNode.set<JsonNode>("full_name", mapper.valueToTree(methodNormalizedFullName))
        jsonNode.set<JsonNode>("args_types", mapper.valueToTree(methodArgsTypes))
        jsonNode.set<JsonNode>("file", mapper.valueToTree(filePath))
        return jsonNode
    }
}

class MethodSummaryStorage(
    private val summaryDumpPath: String,
    private val pathsDumpPath: String,
    private val identityDumpPath: String,
    private val isAstDumpDotFormat: Boolean,
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
    private val identityDumpFile = File(identityDumpPath)
    private val objectMapper = jacksonObjectMapper()

    var methodsNumber: Int = 0
    var pathsNumber: Int = 0

    val size: Int
        get() = data.size

    init {
        summaryDumpFile.createNewFile()
        pathsDumpFile.createNewFile()
        identityDumpFile.createNewFile()
        summaryDumpFile.absolutePath.clearFile()
        pathsDumpFile.absolutePath.clearFile()
        identityDumpFile.absolutePath.clearFile()
    }

    fun add(summary: MethodSummary): Boolean {
        if (contains(summary)) return false
        visited.add(MethodIdentity(summary.filePath, summary.fullName, summary.argsTypes, summary.returnType))
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

    fun dumpVisited() = FileOutputStream(identityDumpFile).bufferedWriter().use { writer ->
        visited.forEach {
            writer.appendLine(it.toJSON(objectMapper).toString())
        }
    }

    private fun dumpToFile(file: File, isMain: Boolean = true) =
        FileOutputStream(file, true).bufferedWriter().use { writer ->
            data.forEach { summary ->
                val node = if (isMain) {
                    summary.toJSONMain(objectMapper, isAstDumpDotFormat)
                } else summary.toJSONPaths(objectMapper)
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

    fun contains(summary: MethodSummary): Boolean =
        contains(summary.filePath, summary.fullName, summary.argsTypes, summary.returnType)

    fun contains(filePath: String, normalizedFullName: String, argsTypes: List<String>, returnType: String?): Boolean =
        visited.contains(MethodIdentity(filePath, normalizedFullName, argsTypes, returnType))

    fun notContains(summary: MethodSummary): Boolean = !contains(summary)
}
