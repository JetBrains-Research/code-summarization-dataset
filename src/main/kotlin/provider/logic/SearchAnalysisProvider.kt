package provider.logic

import analysis.config.AnalysisConfig
import analysis.git.AnalysisRepository
import analysis.logic.Analyzer
import search.config.SearchConfig
import search.logic.ReposFinder
import search.logic.Repository
import java.util.Queue

class SearchAnalysisProvider(
    private val searchConfig: SearchConfig,
    private val analysisConfig: AnalysisConfig
) : Runnable {
    private companion object {
        const val SLEEP_TIME: Long = 15 * 1000L
    }

    private val reposFinder: ReposFinder = ReposFinder(config = searchConfig)
    private val reposAnalyzer: Analyzer = Analyzer(config = analysisConfig)

    private val searchThread: Thread = Thread(reposFinder)
    private val reposQueue = reposFinder.reposStorage.goodReposQueue

    @Volatile var isInterrupted = false

    override fun run() {
        searchThread.start()
        while (isFinderWorking() && !isInterrupted) {
            while (reposQueue.isEmpty() && isFinderWorking() && !isInterrupted) { // active waiting
                Thread.sleep(SLEEP_TIME)
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
        ReposFinder.Status.READY, ReposFinder.Status.WORKING, ReposFinder.Status.LIMITS_WAITING
    ).contains(reposFinder.status)

    fun interrupt() {
        isInterrupted = true
    }
}
