package reposfinder.logic

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import reposfinder.api.APIExtractor
import reposfinder.api.GitHubAPI
import reposfinder.api.GraphQLQueries
import reposfinder.config.SearchConfig
import reposfinder.filtering.Filter
import reposfinder.filtering.FilterResult
import reposfinder.requests.getBody
import reposfinder.requests.getRequest
import reposfinder.requests.isOK
import reposfinder.requests.postRequest
import reposfinder.utils.Logger

class Repository(
    val owner: String,
    val name: String,
    var info: JsonNode,
    var filterResults: MutableList<FilterResult> = mutableListOf(),
    val logger: Logger? = null
) {
    private companion object {
        const val CONTRIBUTORS_CNT = "contributors_count"
        const val COMMITS_CNT = "commits_count"
        const val OWNER = "owner"
        const val NAME = "name"
    }

    fun loadCore(config: SearchConfig, limits: RateLimits, objectMapper: ObjectMapper): Boolean {
        var isCoreGood = true
        var isContributorsGood = true
        if (config.isCore) {
            if (!config.isOnlyContributors) {
                isCoreGood = this.loadCore(objectMapper, config.token)
                limits.core.register()
                Thread.sleep(config.sleepTimeBetweenRequests)
            }
            if (config.isContributors) {
                isContributorsGood = this.loadContributors(config.isAnonContributors, config.token)
                limits.core.register()
                Thread.sleep(config.sleepTimeBetweenRequests)
            }
        }
        return isCoreGood && isContributorsGood
    }

    private fun loadCore(objectMapper: ObjectMapper, token: String? = null): Boolean {
        val (_, response, _) = getRequest(
            url = GitHubAPI.URL.core(owner, name),
            token = token
        )
        if (!response.isOK()) {
            logger?.add("BAD RESPONSE (API v3) for [owner: $owner, name: $name]")
            logger?.add(response.getBody())
            return false
        }
        info = objectMapper.readTree(response.getBody())
        return true
    }

    fun loadGraphQL(config: SearchConfig, limits: RateLimits, objectMapper: ObjectMapper): Boolean {
        var isCommitsGood = true
        if (config.isGraphQL) {
            if (config.isCommitsCount) {
                isCommitsGood = this.loadGraphQL(GraphQLQueries.COMMITS_COUNT, objectMapper, config.token)
                limits.graphQL.register()
                Thread.sleep(config.sleepTimeBetweenRequests)
            }
        }
        return isCommitsGood
    }

    private fun loadGraphQL(requestType: GraphQLQueries, objectMapper: ObjectMapper, token: String? = null): Boolean {
        val query = when (requestType) {
            GraphQLQueries.COMMITS_COUNT -> requestType.getGraphQLBody(
                requestType.query(owner, name, requestType.target),
                objectMapper
            )
        }
        val (_, response, _) = postRequest(
            url = GitHubAPI.GRAPHQL.url,
            jsonBody = query,
            token = token
        )
        if (!response.isOK()) {
            logger?.add("BAD RESPONSE (GraphQL) for [owner: $owner, name: $name]")
            logger?.add(response.getBody())
            return false
        }
        val responseNode = objectMapper.readTree(response.getBody())
        when (requestType) {
            GraphQLQueries.COMMITS_COUNT -> {
                val count = APIExtractor.getCommitsCount(response = responseNode)
                (info as ObjectNode).put(COMMITS_CNT, count)
            }
        }
        return true
    }

    private fun loadContributors(isAnon: Boolean, token: String? = null): Boolean {
        val (_, response, _) = getRequest(
            url = GitHubAPI.URL.contributors(owner, name, isAnon = isAnon),
            token = token
        )
        if (!response.isOK()) {
            logger?.add("BAD RESPONSE (Contributors pagination hack - API v3) for [owner: $owner, name: $name]")
            logger?.add(response.getBody())
            return false
        }
        val count = APIExtractor.getContributorsCount(response = response)
        (info as ObjectNode).put(CONTRIBUTORS_CNT, count)
        return true
    }

    fun isGood(filters: List<Filter>): Boolean {
        var result = true
        filters.forEach { filter ->
            result = filter.isGood(this) && result // we need all filters for statistics
        }
        return result
    }

    override fun toString(): String = mapOf(Pair(OWNER, owner), Pair(NAME, name)).toString()

    fun createSummaryName() = "${this.owner}__${this.name}.json"

    fun toJSONExplain(objectMapper: ObjectMapper? = null): JsonNode {
        val mapper = objectMapper ?: jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
        val node = mapper.createObjectNode()
        node.put(NAME, name)
        node.put(OWNER, owner)
        for (result in filterResults) {
            node.set<JsonNode>(result.field.configName, result.explain(objectMapper))
        }
        return node
    }

    fun toJSON(objectMapper: ObjectMapper? = null, uselessFieldsPrefixes: List<String> = listOf()): JsonNode {
        val mapper = objectMapper ?: jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
        info = APIExtractor.removeUselessFields(info, uselessFieldsPrefixes)
        (info as ObjectNode).set<JsonNode>("explain", toJSONExplain(mapper))
        return info
    }
}
