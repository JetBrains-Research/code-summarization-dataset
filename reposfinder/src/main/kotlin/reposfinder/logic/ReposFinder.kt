package reposfinder.logic

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import reposfinder.api.GraphQLQueries
import reposfinder.config.AnalysisConfig
import reposfinder.filtering.Filter
import reposfinder.utils.Logger
import java.io.File
import java.lang.Integer.max
import java.util.Date

/*
 *  Input URLs format (exactly two slashes):
 *  [...]/{OWNER}/{REPONAME}
 */
class ReposFinder(
    private val config: AnalysisConfig
) : Runnable {
    private companion object {
        const val TOKEN_SHOW_LENGTH = 20
    }

    enum class Status {
        READY,
        WORKING,
        LIMITS_WAITING,
        LIMITS_UPDATE_ERROR,
        BAD_TOKEN_LIMITS,
        INTERRUPTED,
        ERROR,
        DONE
    }

    @Volatile private var status = Status.READY

    private val limits: RateLimits
    private val logger: Logger

    private val dumpDir = File(config.dumpDir)
    val reposStorage: ReposStorage

    private val jsonMapper = jacksonObjectMapper()

    init {
        dumpDir.mkdirs()
        logger = Logger(config.logPath, isDebug = config.isDebug)
        reposStorage = ReposStorage(config.urls, config.dumpDir, config.dumpEveryNRepos, logger = logger)
        limits = RateLimits(config.isCore, config.isGraphQL, config.token, logger = logger)
    }

    override fun run() {
        if (status != Status.READY) {
            return
        }
        status = Status.WORKING
        status = try {
            logStartSummary()
            limits.update()
            logLimits()
            searchRepos()
            reposStorage.dump()
            Status.DONE
        } catch (e: Exception) {
            logger.add("============== ERROR WHILE SEARCH RUNNING ==============")
            logger.add(e.stackTraceToString())
            Status.ERROR
        } finally {
            logEndSummary()
            logger.dump()
        }
    }

    fun interrupt() {
        status = Status.INTERRUPTED
    }

    private fun searchRepos() {
        if (limits.isNoLimits()) {
            status = Status.BAD_TOKEN_LIMITS
            logger.add("ZERO LIMITS ERROR (maybe bad token)")
            return
        }
        for (repo in reposStorage.allRepos) {
            waitingControl(limits.check())
            if (status != Status.WORKING) {
                logger.add("SEARCH WAS INTERRUPTED WITH STATUS $status")
                break
            }
            logger.add("> processing: /${repo.owner}/${repo.name}")

            val isCoreGood = repo.loadCore() && repo.isGood(config.coreFilters)
            val isGraphQLGood = repo.loadGraphQL() && repo.isGood(config.graphQLFilters)

            if (isCoreGood && isGraphQLGood) {
                reposStorage.addGood(repo)
            } else {
                reposStorage.addBad(repo)
            }
        }
    }

    private fun Repository.loadCore(): Boolean {
        var isGood = true
        if (config.isCore) {
            if (!config.isOnlyContributors) {
                isGood = this.loadCore(jsonMapper, config.token) && isGood
                limits.core.register()
                Thread.sleep(config.sleepTimeBetweenRequests)
            }
            if (config.isContributors) {
                isGood = this.loadContributors(config.isAnonContributors, config.token) && isGood
                limits.core.register()
                Thread.sleep(config.sleepTimeBetweenRequests)
            }
        }
        return isGood
    }

    private fun Repository.loadGraphQL(): Boolean {
        var isGood = true
        if (config.isGraphQL) {
            if (config.isCommitsCount) {
                isGood = this.loadGraphQL(GraphQLQueries.COMMITS_COUNT, jsonMapper, config.token) && isGood
                limits.graphQL.register()
                Thread.sleep(config.sleepTimeBetweenRequests)
            }
        }
        return isGood
    }

    private fun Repository.isGood(filters: List<Filter>): Boolean {
        var result = true
        filters.forEach { filter ->
            result = filter.isGood(this) && result
        }
        return result
    }

    private fun waitingControl(resetTime: Long) {
        if (resetTime == RateLimits.NO_TIME) {
            return
        } else if (resetTime == RateLimits.BAD_TIME) {
            status = Status.LIMITS_UPDATE_ERROR
            logger.add("> impossible update API limits")
            logLimits()
        }
        status = Status.LIMITS_WAITING
        logger.add("> per hour requests limits reached")
        logLimits()
        logger.add("> waiting until " + Date(resetTime))
        // until not reset time or not interrupted
        while (System.currentTimeMillis() <= resetTime && status == Status.LIMITS_WAITING) {
            Thread.sleep(config.sleepRange) // sleep in milliseconds
        }
        if (status == Status.LIMITS_WAITING) {
            limits.update()
            status = Status.WORKING
        }
    }

    private fun logStartSummary() {
        logger.add("> search started at " + Date(System.currentTimeMillis()))
        logger.add(
            "> token: ${config.token.substring(0, TOKEN_SHOW_LENGTH)}" +
                "*".repeat(max(0, config.token.length - TOKEN_SHOW_LENGTH))
        )
        logger.add("> filters count: ${config.coreFilters.size + config.graphQLFilters.size}")
        logger.add("> urls count [good: ${reposStorage.goodUrls.size}, bad: ${reposStorage.badUrls.size}]")
        val reposPerHour = config.reposPerHour()
        val totalHours = reposStorage.goodUrls.size.toFloat() / reposPerHour.toFloat()
        logger.add(
            "> estimated time [repos per hour: $reposPerHour, " +
                "total hours: $totalHours] but not more than 2500 per hour (physical limits)"
        )
    }

    private fun logEndSummary() {
        logger.add(
            "> result repos [good: ${reposStorage.goodRepos.size + reposStorage.goodBuffer.size}, " +
                "bad: ${reposStorage.badRepos.size + reposStorage.badBuffer.size}]"
        )
        logLimits()
    }

    private fun logLimits() {
        logger.add("> Core API limits: ${limits.core}")
        logger.add("> GraphQL API limits: ${limits.graphQL}")
    }
}
