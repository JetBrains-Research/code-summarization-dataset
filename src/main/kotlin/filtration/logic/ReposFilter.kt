package filtration.logic

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import filtration.config.FilterConfig
import filtration.utils.prettyDate
import utils.ConsoleLogger
import utils.FileLogger
import java.io.File
import java.lang.Integer.max

/*
 *  Input URLs format (exactly two slashes):
 *  [...]/{OWNER}/{REPONAME}
 */
class ReposFilter(
    private val config: FilterConfig
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

    private val soutLogger: ConsoleLogger
    private val workLogger: FileLogger

    private val dumpDir = File(config.dumpDir)
    private var reposWithoutUpdates = 0

    private val workEnv = WorkEnvironment()

    val reposStorage: ReposStorage

    init {
        dumpDir.mkdirs()
        soutLogger = ConsoleLogger()
        workLogger = FileLogger(config.logPath, isParent = config.isDebug, parentLogger = soutLogger)
        reposStorage = ReposStorage(
            config.urls, config.dumpDir, config.reposDumpThreshold, workEnv = workEnv, logger = workLogger
        )
        limits = RateLimits(config.isCore, config.isGraphQL, config.token, logger = workLogger)
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
            workLogger.add("============== ERROR WHILE SEARCH RUNNING ==============")
            workLogger.add(e.stackTraceToString())
            Status.ERROR
        } finally {
            logEndSummary()
            workLogger.dump()
            workEnv.shutdown()
        }
    }

    fun interrupt() {
        status = Status.INTERRUPTED
        workEnv.shutdown()
    }

    fun awaitUntilNextReadyOrTimeout() {
        workEnv.awaitUntilNextReadyOrTimeout()
    }

    private fun checkLimitsBeforeStart(): Boolean {
        limits.update()
        logLimits()
        if (limits.isNoLimits()) {
            status = Status.BAD_TOKEN_LIMITS
            workLogger.add("ZERO LIMITS ERROR (maybe bad token)")
            return false
        }
        return true
    }

    private fun searchRepos() {
        for (repo in reposStorage.allRepos) {
            limitsUpdateControl()
            waitingControl()
            if (status != Status.WORKING) {
                workLogger.add("SEARCH WAS INTERRUPTED WITH STATUS $status")
                break
            }
            workLogger.add("filtering /${repo.owner}/${repo.name}")
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
            workLogger.add("limits updated")
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
                workLogger.add("impossible update API limits")
                return
            }
        }
        resetTime += RESET_TIME_PAUSE
        status = Status.LIMITS_WAITING
        workLogger.add("per hour requests limits reached")
        logLimits()
        workLogger.add("waiting until ${prettyDate(resetTime)}")
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
        workLogger.add("REPOS FILTER started ${prettyDate(System.currentTimeMillis())}")
        workLogger.add(
            "token: ${config.token.substring(0, TOKEN_SHOW_LENGTH)}" +
                "*".repeat(max(0, config.token.length - TOKEN_SHOW_LENGTH))
        )
        workLogger.add("filters count: ${config.coreFilters.size + config.graphQLFilters.size}")
        workLogger.add("urls count [good: ${reposStorage.goodUrls.size}, bad: ${reposStorage.badUrls.size}]")
        // val reposPerHour = config.reposPerHour()
        // val totalHours = reposStorage.goodUrls.size.toFloat() / reposPerHour.toFloat()
        // logger.add("estimated time [repos per hour: $reposPerHour, total hours: $totalHours]")
    }

    private fun logEndSummary() {
        workLogger.add(
            "result repos [good: ${reposStorage.goodRepos.size + reposStorage.goodBuffer.size}, " +
                "bad: ${reposStorage.badRepos.size + reposStorage.badBuffer.size}]"
        )
        logLimits()
    }

    private fun logLimits() {
        workLogger.add("API LIMITS - Core    ${limits.core}")
        workLogger.add("API LIMITS - GraphQL ${limits.graphQL}")
    }
}
