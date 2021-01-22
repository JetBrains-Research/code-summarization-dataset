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
        const val SLEEP_TIME_WAITING = 30 * 1000L
        const val LOG_FILE_NAME = "main_log.txt"
    }

    private var allPatches = mutableListOf<AnalysisRepository>()
    private var goodPatches = mutableListOf<AnalysisRepository>()
    private var badPatches = mutableListOf<AnalysisRepository>()

    private val parsers = ConcurrentHashMap<Language, Parser<out Node>>()

    private val logger: WorkLogger

    private val pool = Executors.newFixedThreadPool(config.threadsCount)
    private val workers = ConcurrentLinkedQueue<RepoSummarizer>()

    init {
        File(config.dumpFolder).mkdirs()
        logger = WorkLogger(File(config.dumpFolder).resolve(LOG_FILE_NAME).absolutePath, config.isDebug)
        for (lang in Language.values()) {
            parsers[lang] = GumTreeParserFactory.getParser(lang)
        }
        logger.add("> analyzer with ${config.threadsCount} threads loaded at ${Date(System.currentTimeMillis())}")
    }

    fun submit(analysisRepository: AnalysisRepository): Boolean {
        val worker = analysisRepository.constructSummarizer()
        pool.submit(worker)
        workers.add(worker)
        logger.add("> worker for $analysisRepository submitted at ${Date(System.currentTimeMillis())}")
        goodPatches.add(analysisRepository)
        return true
    }

    fun submitAll(repos: List<AnalysisRepository>) = repos.forEach { repo -> submit(repo) }

    fun isAnyRunning(): Boolean {
        val statuses = listOf(
            RepoSummarizer.Status.NOT_INITIALIZED, RepoSummarizer.Status.LOADED,
            RepoSummarizer.Status.RUNNING
        )
        while (workers.peek() != null && !statuses.contains(workers.peek().status)) {
            workers.poll()
        }
        return workers.any { statuses.contains(it.status) }
    }

    fun interrupt() {
        workers.forEach { worker ->
            worker.status = RepoSummarizer.Status.INTERRUPTED
        }
        pool.shutdown()
        dumpLog()
    }

    fun waitUntilAnyRunning() {
        try {
            while (isAnyRunning()) {
                Thread.sleep(SLEEP_TIME_WAITING)
            }
        } catch (e: InterruptedException) {
            // ignore
        } finally {
            interrupt()
        }
    }

    fun dumpLog() = logger.dump()

    private fun AnalysisRepository.constructSummarizer(): RepoSummarizer {
        val repoDumpPath = this.constructDumpPath(config.dumpFolder)
        File(repoDumpPath).mkdirs()
        return RepoSummarizer(this, repoDumpPath, parsers, config)
    }

    private fun AnalysisRepository.isRepoPathGood(): Boolean =
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
