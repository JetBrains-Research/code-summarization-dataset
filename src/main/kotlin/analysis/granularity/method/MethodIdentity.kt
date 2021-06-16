package analysis.granularity.method

import analysis.config.IdentityConfig
import analysis.config.enums.IdentityParameters
import analysis.config.enums.SupportedLanguage
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

data class MethodIdentity(
    var name: String? = null,
    var fullName: String? = null,
    var returnType: String? = null,
    var argsTypes: List<String> = listOf(),
    var filePath: String? = null,
    val language: SupportedLanguage
) {

    companion object {
        // without checking identity parameters => all method parameters
        fun fullIdentity(summary: MethodSummary): MethodIdentity {
            val id = MethodIdentity(
                filePath = summary.filePath,
                name = summary.name,
                fullName = summary.fullName,
                returnType = summary.returnType,
                argsTypes = summary.argsTypes,
                language = summary.language
            )
            id.id = summary.id
            id.isDoc = summary.doc != null
            return id
        }

        // with checking identity parameters => chosen method parameters
        fun configIdentity(summary: MethodSummary, config: IdentityConfig): MethodIdentity {
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

        // with checking identity parameters => chosen method parameters
        fun configIdentity(fullIdentity: MethodIdentity, config: IdentityConfig): MethodIdentity {
            val id = MethodIdentity(language = fullIdentity.language)
            id.config = config
            for (param in config.parameters) {
                when (param) {
                    IdentityParameters.FILE -> id.filePath = fullIdentity.filePath
                    IdentityParameters.NAME -> id.name = fullIdentity.name
                    IdentityParameters.FULL_NAME -> id.fullName = fullIdentity.fullName
                    IdentityParameters.RETURN_TYPE -> id.returnType = fullIdentity.returnType
                    IdentityParameters.ARGS_TYPES -> id.argsTypes = fullIdentity.argsTypes
                }
            }
            return id
        }
    }

    var id: Int? = null
    var isDoc: Boolean = false
    var config: IdentityConfig? = null

    fun toJSON(objectMapper: ObjectMapper? = null): JsonNode {
        val mapper = getObjectMapper(objectMapper)
        val node = mapper.createObjectNode()
        node.set<JsonNode>("id", mapper.valueToTree(id))
        node.set<JsonNode>("is_doc", mapper.valueToTree(isDoc))
        node.set<JsonNode>("name", mapper.valueToTree(name))
        node.set<JsonNode>("full_name", mapper.valueToTree(fullName))
        node.set<JsonNode>("return_type", mapper.valueToTree(returnType))
        node.set<JsonNode>("args_types", mapper.valueToTree(argsTypes))
        node.set<JsonNode>("file", mapper.valueToTree(filePath))
        return node
    }
}
