package reposanalyzer.methods

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.eclipse.jgit.revwalk.RevCommit
import reposanalyzer.config.Language
import reposanalyzer.git.toJSON

/*
 *  name and fullName are normalized names
 */
data class MethodSummary(
    var name: String,
    var fullName: String,
    var filePath: String,
    var language: Language,
    var doc: String? = null,
    var comment: String? = null,
    var body: String? = null,
    var commit: RevCommit? = null,
    var ast: MethodAST? = null,
    var methodParameters: String? = null,
    var parents: List<Pair<String, String>> = listOf()
) {
    fun toJSON(objectMapper: ObjectMapper? = null): JsonNode {
        val mapper = objectMapper ?: jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)

        val jsonNode = mapper.createObjectNode()
        jsonNode.set<JsonNode>("name", mapper.valueToTree(name))
        jsonNode.set<JsonNode>("full_name", mapper.valueToTree(fullName))
        jsonNode.set<JsonNode>("language", mapper.valueToTree(language.label))
        jsonNode.set<JsonNode>("file", mapper.valueToTree(filePath))
        jsonNode.set<JsonNode>("doc", mapper.valueToTree(doc))
        jsonNode.set<JsonNode>("comment", mapper.valueToTree(comment))
        jsonNode.set<JsonNode>("body", mapper.valueToTree(body))
        jsonNode.set<JsonNode>("commit", commit?.toJSON(mapper))
        jsonNode.set<JsonNode>("ast", ast?.toJSON(mapper))

        return jsonNode
    }
}
