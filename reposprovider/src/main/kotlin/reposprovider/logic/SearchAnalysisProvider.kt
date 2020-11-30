package reposprovider.logic

import reposanalyzer.config.AnalysisConfig
import reposanalyzer.logic.RepoInfo
import reposanalyzer.logic.ReposAnalyzer
import reposfinder.config.SearchConfig
import reposfinder.logic.ReposFinder
import reposfinder.logic.Repository
import java.util.Queue

class SearchAnalysisProvider(
    private val searchConfig: SearchConfig,
    private val analysisConfig: AnalysisConfig
) : Runnable {
    private val reposFinder: ReposFinder = ReposFinder(config = searchConfig)
    private val reposAnalyzer: ReposAnalyzer = ReposAnalyzer(config = analysisConfig)

    private val searchThread: Thread = Thread(reposFinder)
    private val reposQueue = reposFinder.reposStorage.goodReposQueue

    private var isInterrupted = false

    override fun run() {
        searchThread.start()
        while (isFinderWorking() && !isInterrupted) {
            while (reposQueue.isEmpty() && isFinderWorking() && !isInterrupted) { // active waiting
                Thread.sleep(30 * 1000)
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

    private fun Queue<Repository>.extractRepoInfos(): List<RepoInfo> {
        val infos = mutableListOf<RepoInfo>()
        while (!this.isEmpty()) {
            val repo = reposQueue.poll()
            infos.add(RepoInfo("", repo.name, repo.owner))
        }
        return infos
    }

    private fun waitWorkers() {
        while (!isInterrupted && reposAnalyzer.isAnyRunning()) {
            Thread.sleep(30 * 1000)
        }
    }

    private fun isFinderWorking() =
        listOf(ReposFinder.Status.READY, ReposFinder.Status.WORKING)
            .contains(reposFinder.status)
}
