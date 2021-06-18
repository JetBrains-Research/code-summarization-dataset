package analysis.granularity.method

import analysis.config.IdentityConfig
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class MethodSummaryStorageStats(
    private val identityConfig: IdentityConfig = IdentityConfig(emptyList())
) {

    val names = mutableSetOf<String>()
    val fullNames = mutableSetOf<String>()
    val visitedFiles = mutableSetOf<String>()
    val langs = mutableSetOf<String>()
    var totalMethods: Int = 0
    var methodsWithDoc: Int = 0
    var methodsWithComment: Int = 0
    var pathsNumber: Int = 0
    var linesNumber: Long = 0L
    val meanLinesLength: Float
        get() = if (totalMethods != 0) (linesNumber.toFloat() / totalMethods) else 0f

    fun registerMethod(summary: MethodSummary) {
        names.add(summary.name)
        fullNames.add(summary.fullName)
        visitedFiles.add(summary.filePath)
        langs.add(summary.language.label)
        summary.id = ++totalMethods
        methodsWithDoc += if (summary.doc != null) 1 else 0
        methodsWithComment += if (summary.comment != null) 1 else 0
        pathsNumber += summary.paths.size
        linesNumber += summary.getLinesNumber()
    }

    fun clear() {
        fullNames.clear()
        visitedFiles.clear()
    }

    fun toJSON(objectMapper: ObjectMapper? = null): JsonNode {
        val mapper = objectMapper ?: jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
        val jsonNode = mapper.createObjectNode()
        jsonNode.set<JsonNode>(
            "method_uniqueness_params",
            mapper.valueToTree(identityConfig.parameters.map { it.label })
        )
        jsonNode.set<JsonNode>("total_methods", mapper.valueToTree(totalMethods))
        jsonNode.set<JsonNode>("total_uniq_names", mapper.valueToTree(names.size))
        jsonNode.set<JsonNode>("total_uniq_full_names", mapper.valueToTree(fullNames.size))
        jsonNode.set<JsonNode>("methods_with_doc", mapper.valueToTree(methodsWithDoc))
        jsonNode.set<JsonNode>("methods_with_comment", mapper.valueToTree(methodsWithComment))
        jsonNode.set<JsonNode>("mean_lines_length", mapper.valueToTree(meanLinesLength))
        jsonNode.set<JsonNode>("total_c2s_paths", mapper.valueToTree(pathsNumber))
        jsonNode.set<JsonNode>("processed_files", mapper.valueToTree(visitedFiles.size))
        jsonNode.set<JsonNode>("processed_languages", mapper.valueToTree(langs))
        return jsonNode
    }
}
