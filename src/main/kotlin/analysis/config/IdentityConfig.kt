package analysis.config

import analysis.config.enums.IdentityParameters
import analysis.utils.BadMethodUniquenessConfigParameter
import com.fasterxml.jackson.databind.JsonNode

class IdentityConfig(val parameters: List<IdentityParameters>) {
    companion object {
        fun createFromJson(jsonNode: JsonNode): IdentityConfig {
            val params = jsonNode.map { it.asText() }
            params.forEach { param ->
                if (!IdentityParameters.values().map { it.label }.contains(param)) {
                    throw BadMethodUniquenessConfigParameter("Unknown identity parameter $param in config list")
                }
            }
            return IdentityConfig(
                IdentityParameters.values().filter { params.contains(it.label) }.toList()
            )
        }
    }

    val isNoIdentity: Boolean
        get() = parameters.isEmpty()
}
