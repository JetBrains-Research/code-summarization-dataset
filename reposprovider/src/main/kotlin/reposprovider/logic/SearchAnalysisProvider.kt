package reposprovider.logic

import reposanalyzer.config.AnalysisConfig
import reposanalyzer.logic.AnalysisRepository
import reposanalyzer.logic.ReposAnalyzer
import reposfinder.config.SearchConfig
import reposfinder.logic.ReposFinder
import reposfinder.logic.Repository
import java.util.Queue

class SearchAnalysisProvider(
    private val searchConfig: SearchConfig,
    private val analysisConfig: AnalysisConfig
) : Runnable {
    private companion object {
        const val SLEEP_TIME: Long = 15 * 1000L
    }

    private val reposFinder: ReposFinder = ReposFinder(config = searchConfig)
    private val reposAnalyzer: ReposAnalyzer = ReposAnalyzer(config = analysisConfig)

    private val searchThread: Thread = Thread(reposFinder)
    private val reposQueue = reposFinder.reposStorage.goodReposQueue

    @Volatile private var isInterrupted = false

    override fun run() {
        searchThread.start()
        while (isFinderWorking() && !isInterrupted) {
            while (reposQueue.isEmpty() && isFinderWorking() && !isInterrupted) { // active waiting
                Thread.sleep(SLEEP_TIME)
            }
            reposAnalyzer.submitAll(reposQueue.extractRepoInfos())
        }
        reposAnalyzer.submitAll(reposQueue.extractRepoInfos())
        waitWorkers()
        reposAnalyzer.interrupt()
    }

    fun interrupt() {
        isInterrupted = true
    }

    private fun Queue<Repository>.extractRepoInfos(): List<AnalysisRepository> {
        val repos = mutableListOf<AnalysisRepository>()
        while (!this.isEmpty()) {
            val repo = reposQueue.poll()
            repos.add(AnalysisRepository("", repo.owner, repo.name))
        }
        return repos
    }

    private fun waitWorkers() {
        while (!isInterrupted && reposAnalyzer.isAnyRunning()) {
            Thread.sleep(SLEEP_TIME)
        }
    }

    private fun isFinderWorking() = listOf(ReposFinder.Status.READY, ReposFinder.Status.WORKING)
        .contains(reposFinder.status)
}
