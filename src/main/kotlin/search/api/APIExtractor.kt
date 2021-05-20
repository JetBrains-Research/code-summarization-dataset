package search.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.kittinunf.fuel.core.Response

object APIExtractor {
    fun getCommitsCount(response: JsonNode): Int = response.get("data")
        ?.get("repository")
        ?.get("defaultBranchRef")
        ?.get("target")
        ?.get("history")
        ?.get("totalCount")?.asInt() ?: 0

    fun getContributorsCount(response: Response): Int {
        if (!response.headers.containsKey("Link")) { // no next pages => only one contributor
            return 1
        }
        val link = response.headers["Link"].toList()[0].split(",")
            .filter {
                it.contains("rel=\"last\"")
            }.toList()[0]
        return link.substringAfter("&page=")
            .substringBefore(">")
            .toInt()
    }

    fun removeUselessFields(jsonNode: JsonNode, suffixes: List<String>): JsonNode {
        val fieldsToRemove = jsonNode.fields().asSequence()
            .toList()
            .map { it.key }
            .filter { field ->
                suffixes.any { suffix ->
                    field.endsWith(suffix)
                }
            }
        for (field in fieldsToRemove) {
            (jsonNode as ObjectNode).remove(field)
        }
        return jsonNode
    }
}
