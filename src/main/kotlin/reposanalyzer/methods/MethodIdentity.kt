package reposanalyzer.methods

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import reposanalyzer.config.IdentityConfig
import reposanalyzer.config.IdentityParameters
import reposanalyzer.config.Language

data class MethodIdentity(
    var name: String? = null,
    var fullName: String? = null,
    var returnType: String? = null,
    var argsTypes: List<String> = listOf(),
    var filePath: String? = null,
    val language: Language
) {

    companion object {
        fun create(summary: MethodSummary, config: IdentityConfig): MethodIdentity {
            val id = MethodIdentity(language = summary.language)
            id.config = config
            id.id = summary.id
            id.isDoc = summary.doc != null
            for (param in config.parameters) {
                when (param) {
                    IdentityParameters.FILE -> id.filePath = summary.filePath
                    IdentityParameters.NAME -> id.name = summary.name
                    IdentityParameters.FULL_NAME -> id.fullName = summary.fullName
                    IdentityParameters.RETURN_TYPE -> id.returnType = summary.returnType
                    IdentityParameters.ARGS_TYPES -> id.argsTypes = summary.argsTypes
                }
            }
            return id
        }

        fun create(realId: MethodIdentity, config: IdentityConfig): MethodIdentity {
            val id = MethodIdentity(language = realId.language)
            id.config = config
            for (param in config.parameters) {
                when (param) {
                    IdentityParameters.FILE -> id.filePath = realId.filePath
                    IdentityParameters.NAME -> id.name = realId.name
                    IdentityParameters.FULL_NAME -> id.fullName = realId.fullName
                    IdentityParameters.RETURN_TYPE -> id.returnType = realId.returnType
                    IdentityParameters.ARGS_TYPES -> id.argsTypes = realId.argsTypes
                }
            }
            return id
        }
    }

    var id: Int? = null
    var isDoc: Boolean? = null
    var config: IdentityConfig? = null

    fun toJSON(objectMapper: ObjectMapper? = null): JsonNode {
        val mapper = getObjectMapper(objectMapper)
        val node = mapper.createObjectNode()
        node.set<JsonNode>("id", mapper.valueToTree(id))
        node.set<JsonNode>("is_doc", mapper.valueToTree(isDoc))
        config?.parameters?.forEach {
            when (it) {
                IdentityParameters.FILE -> node.set<JsonNode>("file", mapper.valueToTree(filePath))
                IdentityParameters.NAME -> node.set<JsonNode>("name", mapper.valueToTree(name))
                IdentityParameters.FULL_NAME -> node.set<JsonNode>("full_name", mapper.valueToTree(fullName))
                IdentityParameters.RETURN_TYPE -> node.set<JsonNode>("return_type", mapper.valueToTree(returnType))
                IdentityParameters.ARGS_TYPES -> node.set<JsonNode>("args_types", mapper.valueToTree(argsTypes))
            }
        }
        return node
    }
}
