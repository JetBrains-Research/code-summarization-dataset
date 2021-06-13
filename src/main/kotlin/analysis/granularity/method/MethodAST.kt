package analysis.granularity.method

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/*
 *  methodName is normalized name
 */
data class MethodAST(
    val methodName: String,
    val graph: Map<Long, List<Long>>,
    val description: List<MethodToken>
) {
    fun toJSON(objectMapper: ObjectMapper? = null, dotVersion: Boolean = false): JsonNode {
        val mapper = objectMapper ?: jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)

        val sortedTokens = description.sortedBy { it.id }

        return if (dotVersion) {
            val jsonNode = mapper.createObjectNode()
            val tokensMap = mutableMapOf<Long, Map<String, String>>()
            sortedTokens.forEach {
                tokensMap[it.id] = it.toMap()
            }
            jsonNode.set<JsonNode>("graph", mapper.valueToTree(graph))
            jsonNode.set<JsonNode>("tokens", mapper.valueToTree(tokensMap))
            jsonNode
        } else {
            val tokensList = mutableListOf<JsonNode>()
            sortedTokens.forEach { token ->
                val tokenNode = mapper.createObjectNode()
                tokenNode.set<JsonNode>("id", mapper.valueToTree(token.id))
                tokenNode.set<JsonNode>("token", mapper.valueToTree(token.name))
                tokenNode.set<JsonNode>("type", mapper.valueToTree(token.type))
                tokenNode.set<JsonNode>("children", mapper.valueToTree(graph[token.id]))
                tokensList.add(tokenNode)
            }
            mapper.valueToTree(tokensList)
        }
    }
}
