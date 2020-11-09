package reposanalyzer.methods

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/*
 *   uniqueness checks through pair: [method normalized full name, filepath to file with method]
 */
class MethodSummaryStorage {
    private data class Identity(val methodNormalizedFullName: String, val filePath: String)

    private val visited = mutableSetOf<Identity>()
    private val data = mutableSetOf<MethodSummary>()

    val size: Int
        get() = data.size

    fun add(summary: MethodSummary): Boolean {
        if (!contains(summary)) {
            visited.add(Identity(summary.fullName, summary.filePath))
            data.add(summary)
            return true
        }
        return false
    }

    fun toJSON(objectMapper: ObjectMapper? = null): List<JsonNode> {
        val mapper = objectMapper ?: jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
        return data.map { it.toJSON(mapper) }.toList()
    }

    fun contains(summary: MethodSummary): Boolean {
        return contains(summary.fullName, summary.filePath)
    }

    fun contains(normalizedFullName: String, filePath: String): Boolean {
        return visited.contains(Identity(normalizedFullName, filePath))
    }

    fun notContains(summary: MethodSummary): Boolean {
        return !contains(summary)
    }

    fun notContains(normalizedFullName: String, filePath: String): Boolean {
        return !contains(normalizedFullName, filePath)
    }
}
