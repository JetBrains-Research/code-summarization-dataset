package reposfinder.logic

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import reposfinder.api.GitHubAPI
import reposfinder.requests.getBody
import reposfinder.requests.getRequest
import reposfinder.requests.isOK
import reposfinder.utils.Logger
import reposfinder.utils.prettyDate
import kotlin.math.max
import kotlin.math.min

class RateLimits(
    private val isCore: Boolean,
    private val isGraphQL: Boolean,
    private val token: String,
    private val remainingMin: Int = 100,
    private val logger: Logger? = null
) {
    companion object {
        const val RESOURCES = "resources"
        const val CORE = "core"
        const val GRAPHQL = "graphql"
        const val LIMIT = "limit"
        const val USED = "used"
        const val REMAINING = "remaining"
        const val RESET = "reset"
        const val UPDATE_SLEEP = 5 * 1000L
        const val ONE_SECOND_IN_MILLIS = 1000L
        const val NO_LIMITS = 0L
        const val BAD_TIME = -1L
    }

    data class Limits(
        var limit: Int = 0,
        var used: Int = 0,
        var remaining: Int = 0,
        var resetTime: Long = 0, // milliseconds from 01.01.1970
        val remainingMin: Int
    ) {
        fun register(count: Int = 1) {
            remaining = max(0, remaining - count)
            used = min(used + count, limit)
        }

        fun isRemaining(): Boolean = remaining > remainingMin

        override fun toString(): String {
            return "[limit: $limit, used: $used, remaining: $remaining, reset: ${prettyDate(resetTime)}]"
        }
    }

    private val objectMapper = jacksonObjectMapper()

    val core = Limits(remainingMin = remainingMin)
    val graphQL = Limits(remainingMin = remainingMin)

    fun check(): Long {
        var resetTime: Long = NO_LIMITS
        if (isCore && !core.isRemaining()) {
            if (!tryUpdate()) {
                resetTime = BAD_TIME
            } else if (core.limit == 0) {
                resetTime = BAD_TIME
            } else if (!core.isRemaining()) {
                resetTime = max(resetTime, core.resetTime)
            }
        }
        if (isGraphQL && !graphQL.isRemaining() && resetTime != BAD_TIME) {
            if (!tryUpdate()) {
                resetTime = BAD_TIME
            } else if (graphQL.limit == 0) {
                resetTime = BAD_TIME
            } else if (!graphQL.isRemaining()) {
                resetTime = max(resetTime, graphQL.resetTime)
            }
        }
        return resetTime
    }

    fun isNoLimits() = this.core.limit == 0 || this.graphQL.limit == 0

    fun update(): Boolean {
        val (_, response, _) = getRequest(
            url = GitHubAPI.LIMITS.url,
            token = token
        )
        val body = response.getBody()
        if (!response.isOK()) {
            logger?.add("BAD LIMITS UPDATE RESPONSE")
            logger?.add(body)
            return false
        }
        val node = objectMapper.readTree(body)
        val isCore = core.updateByNode(CORE, node)
        val isGraphQL = graphQL.updateByNode(GRAPHQL, node)
        if (!isCore && !isGraphQL) {
            logger?.add("IMPOSSIBLE UPDATE API LIMITS (maybe wrong token)")
            logger?.add(body)
        }
        return isCore && isGraphQL
    }

    private fun tryUpdate(): Boolean {
        if (!update()) {
            Thread.sleep(UPDATE_SLEEP)
            return update()
        }
        return true
    }

    private fun Limits.updateByNode(api: String, jsonNode: JsonNode?): Boolean {
        val node = jsonNode?.get(RESOURCES)?.get(api) ?: return false
        this.limit = node.get(LIMIT).asInt()
        this.used = node.get(USED).asInt()
        this.remaining = node.get(REMAINING).asInt()
        this.resetTime = node.get(RESET).asLong() * ONE_SECOND_IN_MILLIS // to milliseconds from seconds
        return true
    }
}
