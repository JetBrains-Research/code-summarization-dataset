package reposfinder.logic

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import reposfinder.api.GraphQLQueries
import reposfinder.config.SearchConfig
import reposfinder.config.WorkConfig
import reposfinder.filtering.Filter
import java.io.File

/*
 *  Input URLs format (exactly two slashes):
 *  [...]/{OWNER}/{REPONAME}
 */
class ReposFinder(
    private val urls: List<String>,
    private val workConfig: WorkConfig,
    private val searchConfig: SearchConfig,
) : Runnable {

    enum class Status {
        READY,
        WORKING,
        LIMIT_WAITING,
        INTERRUPTED,
        ERROR,
        DONE
    }

    private var status = Status.READY
    private val jsonMapper = jacksonObjectMapper()
    private val dumpDir = File(workConfig.dumpDir)

    private val limits = RateLimits(
        isCore = searchConfig.isCore,
        isGraphQL = searchConfig.isGraphQL,
        token = workConfig.token // !!!
    )

    private val reposStorage = ReposStorage(urls, workConfig.dumpDir, workConfig.dumpEveryNRepos)

    init {
        dumpDir.mkdirs()
        println("token: ${workConfig.token}")
    }

    override fun run() {
        if (status != Status.READY) {
            return
        }
        status = Status.WORKING
        limits.update()
        searchRepos()
        reposStorage.dump()
        status = Status.DONE
    }

    private fun searchRepos() {
        println("Core: ${limits.core}")
        println("GraphQL: ${limits.graphQL}")
        for (repo in reposStorage.allRepos) {
            println("processing: /${repo.owner}/${repo.name}")
            waitingControl(limits.check())

            repo.loadCore()
            val isCoreGood = repo.isGood(searchConfig.coreFilters)
            repo.loadGraphQL()
            val isGraphQLGood = repo.isGood(searchConfig.graphQLFilters)

            if (isCoreGood && isGraphQLGood) {
                reposStorage.addGood(repo)
            } else {
                reposStorage.addBad(repo)
            }
        }
    }

    private fun Repository.loadCore() {
        if (searchConfig.isCore) {
            if (!searchConfig.isOnlyContributors) {
                this.loadCore(jsonMapper, workConfig.token)
                limits.registerCore()
                Thread.sleep(workConfig.waitsBetweenRequests)
            }
            if (searchConfig.isContributors) {
                this.loadContributors(searchConfig.isAnonContributors, workConfig.token)
                limits.registerCore()
                Thread.sleep(workConfig.waitsBetweenRequests)
            }
        }
    }

    private fun Repository.loadGraphQL() {
        if (searchConfig.isGraphQL) {
            if (searchConfig.isCommitsCount) {
                this.loadGraphQL(GraphQLQueries.COMMITS_COUNT, jsonMapper, workConfig.token)
                limits.registerGraphQL()
                Thread.sleep(workConfig.waitsBetweenRequests)
            }
        }
    }

    private fun Repository.isGood(filters: List<Filter>): Boolean {
        var result = true
        for (filter in filters) {
            result = filter.isGood(this) && result
        }
        return result
    }

    fun interrupt() {
        status = Status.INTERRUPTED
    }

    private fun waitingControl(resetTime: Long) {
        if (resetTime == 0L) {
            return
        }
        status = Status.LIMIT_WAITING
        // while not reset time or not interrupted
        while (System.currentTimeMillis() <= resetTime && status != Status.INTERRUPTED) {
            // sleep in milliseconds
            Thread.sleep(workConfig.sleepRange)
        }
        if (status == Status.LIMIT_WAITING) {
            status = Status.WORKING
        }
    }
}
