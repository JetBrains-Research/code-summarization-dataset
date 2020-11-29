package reposanalyzer.logic

import reposanalyzer.config.SearchConfig
import reposanalyzer.utils.WorkLogger
import reposanalyzer.utils.isDotGitPresent
import java.io.File
import java.util.Date
import java.util.concurrent.ConcurrentLinkedQueue

class ReposAnalyzer(val config: SearchConfig) : Runnable {
    private companion object {
        const val LOG_FILE_NAME = "main_log.txt"
    }

    private var repoPatches = mutableListOf<String>()
    private var goodPatches = mutableListOf<String>()
    private var badPatches = mutableListOf<String>()
    private val logger: WorkLogger

    val addedWorkers = ConcurrentLinkedQueue<RepoSummarizer>()
    val startedWorkers = ConcurrentLinkedQueue<RepoSummarizer>()
    val isReady = false

    init {
        File(config.dumpFolder).mkdirs()
        logger = WorkLogger(config.dumpFolder + File.separator + LOG_FILE_NAME)
        logger.add("> analyzer loaded at ${Date(System.currentTimeMillis())}")
    }

    override fun run() {
        logger.add("> analyzer running")
        while (!addedWorkers.isEmpty()) {
            val worker = addedWorkers.poll()
            worker.init()
            // ADD TO THREAD POOL TODO
            worker.run()
            startedWorkers.add(worker)
        }
        logger.add("> analyzer done")
        logger.dump()
    }

    fun addRepo(repo: RepoInfo): Boolean {
        if (!repo.path.isRepoPathGood()) {
            return false
        }
        addedWorkers.add(repo.constructSummarizer())
        return true
    }

    fun addAllRepos(repos: List<RepoInfo>) = repos.forEach { repo -> addRepo(repo) }

    private fun RepoInfo.constructSummarizer(): RepoSummarizer {
        val repoDumpPath = this.constructDumpPath(config.dumpFolder)
        File(repoDumpPath).mkdirs()
        return RepoSummarizer(this, repoDumpPath, config)
    }

    private fun String.isRepoPathGood(): Boolean =
        if (repoPatches.contains(this)) {
            logger.add("> path already added: $this")
            false
        } else if (!this.isDotGitPresent()) {
            logger.add("> repo path hasn't .git folder: $this")
            repoPatches.add(this)
            badPatches.add(this)
            false
        } else {
            repoPatches.add(this)
            goodPatches.add(this)
            logger.add("> path is good: $this")
            true
        }
}
