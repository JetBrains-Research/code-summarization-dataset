package reposanalyzer.logic

import astminer.common.model.Node
import astminer.common.model.Parser
import reposanalyzer.config.AnalysisConfig
import reposanalyzer.config.Language
import reposanalyzer.parsing.GumTreeParserFactory
import reposanalyzer.utils.WorkLogger
import reposanalyzer.utils.isDotGitPresent
import java.io.File
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class ReposAnalyzer(
    val config: AnalysisConfig
) {
    private companion object {
        const val LOG_FILE_NAME = "main_log.txt"
    }

    private var allPatches = mutableListOf<RepoInfo>()
    private var goodPatches = mutableListOf<RepoInfo>()
    private var badPatches = mutableListOf<RepoInfo>()

    private val parsers = ConcurrentHashMap<Language, Parser<out Node>>()

    private val logger: WorkLogger

    val pool = Executors.newFixedThreadPool(config.threadsCount)
    val workers = ConcurrentLinkedQueue<RepoSummarizer>()

    init {
        File(config.dumpFolder).mkdirs()
        logger = WorkLogger(config.dumpFolder + File.separator + LOG_FILE_NAME, config.isDebug)
        for (lang in Language.values()) {
            parsers[lang] = GumTreeParserFactory.getParser(lang)
        }
        logger.add("> analyzer witch ${config.threadsCount} threads loaded at ${Date(System.currentTimeMillis())}")
    }

    fun submit(repoInfo: RepoInfo): Boolean {
        val worker = repoInfo.constructSummarizer()
        pool.submit(worker)
        workers.add(worker)
        logger.add("> worker for $repoInfo submitted at ${Date(System.currentTimeMillis())}")
        goodPatches.add(repoInfo)
        return true
    }

    fun submitAll(repos: List<RepoInfo>) = repos.forEach { repo -> submit(repo) }

    fun isAnyRunning(): Boolean {
        val statuses = listOf(
            RepoSummarizer.Status.NOT_INITIALIZED, RepoSummarizer.Status.LOADED,
            RepoSummarizer.Status.RUNNING
        )
        return workers.any { statuses.contains(it.status) }
    }

    fun interrupt() {
        workers.forEach { worker ->
            worker.status = RepoSummarizer.Status.INTERRUPTED
        }
        pool.shutdown()
        dumpLog()
    }

    fun dumpLog() = logger.dump()

    private fun RepoInfo.constructSummarizer(): RepoSummarizer {
        val repoDumpPath = this.constructDumpPath(config.dumpFolder)
        File(repoDumpPath).mkdirs()
        return RepoSummarizer(this, repoDumpPath, parsers, config)
    }

    private fun RepoInfo.isRepoPathGood(): Boolean =
        if (allPatches.contains(this)) {
            logger.add("> path already added: $this")
            false
        } else if (!this.path.isDotGitPresent()) {
            logger.add("> repo path hasn't .git folder: $this")
            false
        } else {
            logger.add("> path is good: $this")
            true
        }
}
