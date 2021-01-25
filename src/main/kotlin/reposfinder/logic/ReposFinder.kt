package reposfinder.logic

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import reposfinder.config.SearchConfig
import reposfinder.utils.Logger
import java.io.File
import java.lang.Integer.max
import java.util.Date

/*
 *  Input URLs format (exactly two slashes):
 *  [...]/{OWNER}/{REPONAME}
 */
class ReposFinder(
    private val config: SearchConfig
) : Runnable {
    private companion object {
        const val TOKEN_SHOW_LENGTH = 20
        const val RESET_TIME_PAUSE = 30 * 1000L
        const val UPDATE_REQUEST_EVERY_N_REPOS = 20
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

    @Volatile var status = Status.READY

    private val jsonMapper = jacksonObjectMapper()
    private val limits: RateLimits
    private val logger: Logger

    private val dumpDir = File(config.dumpDir)
    private var reposWithoutUpdates = 0

    val reposStorage: ReposStorage

    init {
        dumpDir.mkdirs()
        logger = Logger(config.logPath, isDebug = config.isDebug)
        reposStorage = ReposStorage(config.urls, config.dumpDir, config.reposDumpThreshold, logger = logger)
        limits = RateLimits(config.isCore, config.isGraphQL, config.token, logger = logger)
    }

    override fun run() {
        if (status != Status.READY) {
            return
        }
        status = Status.WORKING
        logStartSummary()
        if (!checkLimitsBeforeStart()) {
            return
        }
        status = try {
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

    private fun checkLimitsBeforeStart(): Boolean {
        limits.update()
        logLimits()
        if (limits.isNoLimits()) {
            status = Status.BAD_TOKEN_LIMITS
            logger.add("ZERO LIMITS ERROR (maybe bad token)")
            return false
        }
        return true
    }

    private fun searchRepos() {
        for (repo in reposStorage.allRepos) {
            limitsUpdateControl()
            waitingControl()
            if (status != Status.WORKING) {
                logger.add("SEARCH WAS INTERRUPTED WITH STATUS $status")
                break
            }
            logger.add("> processing /${repo.owner}/${repo.name}")
            val isCoreGood = repo.loadCore(config, limits, jsonMapper) && repo.isGood(config.coreFilters)
            val isGraphQLGood = repo.loadGraphQL(config, limits, jsonMapper) && repo.isGood(config.graphQLFilters)
            if (isCoreGood && isGraphQLGood) {
                reposStorage.addGood(repo)
            } else {
                reposStorage.addBad(repo)
            }
            reposWithoutUpdates++
        }
    }

    private fun limitsUpdateControl() {
        if (reposWithoutUpdates >= UPDATE_REQUEST_EVERY_N_REPOS) {
            limits.update()
            logger.add("> limits updated")
            logLimits()
            reposWithoutUpdates = 0
        }
    }

    private fun waitingControl() {
        var resetTime = limits.check()
        when (resetTime) {
            RateLimits.NO_LIMITS -> return
            RateLimits.BAD_TIME -> {
                status = Status.LIMITS_UPDATE_ERROR
                logger.add("> impossible update API limits")
                return
            }
        }
        resetTime += RESET_TIME_PAUSE
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
        logger.add("> limits API - Core    ${limits.core}")
        logger.add("> limits API - GraphQL ${limits.graphQL}")
    }
}
