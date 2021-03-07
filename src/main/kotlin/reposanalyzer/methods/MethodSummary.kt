package reposanalyzer.methods

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.eclipse.jgit.revwalk.RevCommit
import reposanalyzer.config.Language
import reposanalyzer.git.toJSONMain

/*
 *  name and fullName are normalized names
 */
data class MethodSummary(
    var name: String,
    var fullName: String,
    var filePath: String,
    var splitName: String,
    var language: Language,
    var argsTypes: List<String> = emptyList(),
    var returnType: String? = null,
    var id: Int? = null,
    var repoOwner: String? = null,
    var repoName: String? = null,
    var repoLicense: String? = null,
    var doc: String? = null,
    var comment: String? = null,
    var posInFile: Int? = null,
    var length: Int? = null,
    var body: String? = null,
    var paths: List<String> = emptyList(),
    var firstLineInFile: Int? = null,
    var lastLineInFile: Int? = null,
    var commit: RevCommit? = null,
    var ast: MethodAST? = null,
    var methodParameters: String? = null,
    var parents: List<Pair<String, String>> = listOf()
) {
    private companion object {
        const val GIT_HUB = "https://github.com"
    }

    fun createUrl(): String? =
        if (repoName == null || repoOwner == null || commit == null) {
            null
        } else {
            "$GIT_HUB/$repoOwner/$repoName/blob/${commit?.name}/$filePath#L$firstLineInFile-L$lastLineInFile"
        }

    fun getLinesNumber() =
        if (firstLineInFile != null && lastLineInFile != null) lastLineInFile!! - firstLineInFile!! + 1 else 0

    fun toC2SPaths(): String? = if (paths.isEmpty()) null else splitName + " " + paths.joinToString(" ")

    fun toJSONMain(objectMapper: ObjectMapper? = null, isAstDotFormat: Boolean = false): JsonNode {
        val mapper = getObjectMapper(objectMapper)
        val jsonNode = toJSONCommon(mapper) as ObjectNode

        jsonNode.set<JsonNode>("url", mapper.valueToTree(createUrl()))
        jsonNode.set<JsonNode>("commit", commit?.toJSONMain(mapper))
        jsonNode.set<JsonNode>("doc", mapper.valueToTree(doc))
        jsonNode.set<JsonNode>("comment", mapper.valueToTree(comment))
        jsonNode.set<JsonNode>("body_lines", mapper.valueToTree(getLinesNumber()))
        jsonNode.set<JsonNode>("body", mapper.valueToTree(body))
        jsonNode.set<JsonNode>("ast", ast?.toJSON(mapper, isAstDotFormat))
        return jsonNode
    }

    fun toJSONPaths(objectMapper: ObjectMapper? = null): JsonNode {
        val mapper = getObjectMapper(objectMapper)
        val jsonNode = toJSONCommon(mapper) as ObjectNode
        jsonNode.set<JsonNode>("paths", mapper.valueToTree(paths))
        return jsonNode
    }

    private fun toJSONCommon(objectMapper: ObjectMapper? = null): JsonNode {
        val mapper = getObjectMapper(objectMapper)
        val jsonNode = mapper.createObjectNode()

        id?.let { jsonNode.set<JsonNode>("id", mapper.valueToTree(id)) }
        jsonNode.set<JsonNode>("name", mapper.valueToTree(name))
        jsonNode.set<JsonNode>("spl_name", mapper.valueToTree(splitName))
        jsonNode.set<JsonNode>("full_name", mapper.valueToTree(fullName))
        jsonNode.set<JsonNode>("language", mapper.valueToTree(language.label))
        jsonNode.set<JsonNode>("args_types", mapper.valueToTree(argsTypes))
        jsonNode.set<JsonNode>("return_type", mapper.valueToTree(returnType))
        jsonNode.set<JsonNode>("file", mapper.valueToTree(filePath))
        jsonNode.set<JsonNode>("repo", mapper.valueToTree("/$repoOwner/$repoName"))
        jsonNode.set<JsonNode>("repo_license", mapper.valueToTree(repoLicense))
        return jsonNode
    }
}
