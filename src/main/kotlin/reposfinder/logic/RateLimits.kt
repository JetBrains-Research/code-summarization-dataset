package reposfinder.logic

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import reposfinder.api.GitHubAPI
import reposfinder.requests.getBody
import reposfinder.requests.getRequest
import reposfinder.requests.isOK
import kotlin.math.max
import kotlin.math.min

class RateLimits(
    private val isCore: Boolean,
    private val isGraphQL: Boolean,
    private val token: String? = null,
    private val remainingMin: Int = 20
) {
    private companion object {
        const val RESOURCES = "resources"
        const val CORE = "core"
        const val GRAPHQL = "graphql"
        const val LIMIT = "limit"
        const val USED = "used"
        const val REMAINING = "remaining"
        const val RESET = "reset"
        const val ONE_SECOND_IN_MILLIS = 1000
        const val ZERO = 0
    }

    data class Limits(
        var limit: Int = 0,
        var used: Int = 0,
        var remaining: Int = 0,
        var resetTime: Long = 0 // milliseconds from 01.01.1970
    )

    private val objectMapper = jacksonObjectMapper()

    val core = Limits()
    val graphQL = Limits()

    fun check(): Long {
        var resetTime: Long = 0
        if (isCore && checkCore()) {
            update()
            if (checkCore()) {
                resetTime = max(resetTime, core.resetTime)
            }
        }
        if (isGraphQL && checkGraphQL()) {
            update()
            if (checkGraphQL()) {
                resetTime = max(resetTime, graphQL.resetTime)
            }
        }
        return resetTime
    }

    fun update(): Boolean {
        val (_, response, _) = getRequest(
            url = GitHubAPI.LIMITS.url,
            token = token
        )
        if (!response.isOK()) {
            return false
        }
        val body = response.getBody()
        val node = objectMapper.readTree(body)
        val isCore = core.updateByNode(CORE, node)
        val isGraphQL = graphQL.updateByNode(GRAPHQL, node)
        return isCore && isGraphQL
    }

    fun registerCore(count: Int = 1) {
        core.remaining = max(0, core.remaining - count)
        core.used = min(core.used + count, core.limit)
    }

    fun registerGraphQL(count: Int = 1) {
        graphQL.remaining = max(0, graphQL.remaining - count)
        graphQL.used = min(graphQL.used + count, graphQL.limit)
    }

    private fun checkCore() = core.remaining <= remainingMin && core.limit != ZERO

    private fun checkGraphQL() = graphQL.remaining <= remainingMin && graphQL.limit != ZERO

    private fun Limits.updateByNode(api: String, jsonNode: JsonNode?): Boolean {
        val node = jsonNode?.get(RESOURCES)?.get(api) ?: return false
        this.limit = node.get(LIMIT).asInt()
        this.used = node.get(USED).asInt()
        this.remaining = node.get(REMAINING).asInt()
        this.resetTime = node.get(RESET).asLong() * ONE_SECOND_IN_MILLIS // to milliseconds from seconds
        return true
    }
}
