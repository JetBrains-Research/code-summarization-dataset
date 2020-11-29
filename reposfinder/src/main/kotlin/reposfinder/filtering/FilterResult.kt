package reposfinder.filtering

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class FilterResult(
    val field: Field,
    val repoValue: String,
    val filterValueMin: String,
    val filterValueMax: String? = null,
    val relation: Relation? = null,
    val result: Boolean = false
) {
    fun explain(objectMapper: ObjectMapper? = null): JsonNode {
        val mapper = objectMapper ?: jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)

        val node = mapper.createObjectNode()
        node.put("result", if (result) "OK" else "BAD")
        node.put("repo_value", repoValue)
        if (filterValueMax != null) {
            node.set(
                "filter_range",
                mapper.valueToTree(listOf(filterValueMin, filterValueMax))
            )
        } else if (relation != null) {
            node.put("relation", relation.sign)
            node.put("filter_value", filterValueMin)
        }
        return node
    }
}
