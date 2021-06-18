package provider.logic

import analysis.config.AnalysisConfig
import analysis.git.AnalysisRepository
import analysis.logic.Analyzer
import filtration.config.FilterConfig
import filtration.logic.ReposFilter
import filtration.logic.Repository
import java.util.Queue

class FilterAnalyserProvider(
    private val filterConfig: FilterConfig,
    private val analysisConfig: AnalysisConfig
) : Runnable {

    private val reposFilter: ReposFilter = ReposFilter(config = filterConfig)
    private val reposAnalyzer: Analyzer = Analyzer(config = analysisConfig)

    private val searchThread: Thread = Thread(reposFilter)
    private val reposQueue = reposFilter.reposStorage.goodReposQueue

    @Volatile var isInterrupted = false

    override fun run() {
        searchThread.start()
        while (isFinderWorking() && !isInterrupted) {
            while (reposQueue.isEmpty() && isFinderWorking() && !isInterrupted) { // active waiting
                reposFilter.awaitUntilNextReadyOrTimeout()
            }
            reposAnalyzer.submitRepos(reposQueue.extractRepoInfos())
        }
        reposAnalyzer.submitRepos(reposQueue.extractRepoInfos())
        reposAnalyzer.waitUnitAnyRunning()
    }

    private fun Queue<Repository>.extractRepoInfos(): List<AnalysisRepository> {
        val repos = mutableListOf<AnalysisRepository>()
        while (!this.isEmpty()) {
            val repo = reposQueue.poll()
            repos.add(AnalysisRepository("", repo.owner, repo.name, repo.license))
        }
        return repos
    }

    private fun isFinderWorking() = listOf(
        ReposFilter.Status.READY, ReposFilter.Status.WORKING, ReposFilter.Status.LIMITS_WAITING
    ).contains(reposFilter.status)

    fun interrupt() {
        isInterrupted = true
        reposFilter.interrupt()
        reposAnalyzer.shutdown()
    }
}
