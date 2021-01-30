package reposanalyzer.logic

import astminer.common.model.Node
import astminer.common.model.Parser
import reposanalyzer.config.AnalysisConfig
import reposanalyzer.config.Language
import reposanalyzer.git.isDotGitPresent
import reposanalyzer.parsing.GumTreeParserFactory
import reposanalyzer.utils.WorkLogger
import reposanalyzer.utils.appendLines
import reposanalyzer.utils.clearFile
import java.io.File
import java.util.Date
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class ReposAnalyzer(
    val config: AnalysisConfig
) {
    private companion object {
        const val SLEEP_TIME_WAITING = 30 * 1000L
        const val LOG_FILE_NAME = "work_log.txt"
        const val DONE_LOG_FILE_NAME = "done_log.txt"
    }

    private val runStatuses = listOf(
        RepoSummarizer.Status.NOT_INITIALIZED, RepoSummarizer.Status.LOADED, RepoSummarizer.Status.RUNNING
    )

    private val doneWorkersLogFile = File(config.dumpFolder).resolve(DONE_LOG_FILE_NAME)

    private var allPatches = mutableListOf<AnalysisRepository>()
    private var goodPatches = mutableListOf<AnalysisRepository>()
    private var badPatches = mutableListOf<AnalysisRepository>()

    private val parsers = ConcurrentHashMap<Language, Parser<out Node>>()

    private val logger: WorkLogger

    private val pool = Executors.newFixedThreadPool(config.threadsCount)
    private val workers = LinkedList<RepoSummarizer>()
    private val doneWorkers = LinkedList<RepoSummarizer>()

    init {
        File(config.dataDumpFolder).mkdirs()
        doneWorkersLogFile.createNewFile()
        doneWorkersLogFile.absolutePath.clearFile()
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
        return workers.any { runStatuses.contains(it.status) }
    }

    fun processDoneWorkers() {
        val iter = workers.iterator()
        while (iter.hasNext()) {
            val worker = iter.next()
            if (!runStatuses.contains(worker.status)) {
                doneWorkers.add(worker)
                iter.remove()
            }
        }
        val toDump = mutableListOf<String>()
        while (!doneWorkers.isEmpty()) {
            val worker = doneWorkers.poll()
            val repo = worker.analysisRepo
            toDump.add("${worker.status},${repo.owner}/${repo.name}")
        }
        doneWorkersLogFile.appendLines(toDump)
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
                processDoneWorkers()
                Thread.sleep(SLEEP_TIME_WAITING)
            }
            processDoneWorkers()
        } catch (e: InterruptedException) {
            // ignore
        } finally {
            interrupt()
        }
    }

    private fun dumpLog() = logger.dump()

    private fun AnalysisRepository.constructSummarizer(): RepoSummarizer {
        val repoDumpPath = this.constructDumpPath(config.dataDumpFolder)
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
