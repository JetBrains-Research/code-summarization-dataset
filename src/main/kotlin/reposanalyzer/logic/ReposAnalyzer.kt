package reposanalyzer.logic

import astminer.common.model.Node
import astminer.common.model.Parser
import reposanalyzer.config.AnalysisConfig
import reposanalyzer.config.Language
import reposanalyzer.logic.summarizers.HistorySummarizer
import reposanalyzer.logic.summarizers.NoHistorySummarizer
import reposanalyzer.logic.summarizers.Summarizer
import reposanalyzer.logic.summarizers.SummarizerStatus
import reposanalyzer.parsing.GumTreeParserFactory
import reposanalyzer.parsing.SafeParser
import reposanalyzer.utils.WorkLogger
import reposanalyzer.utils.prettyDate
import java.io.File
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ReposAnalyzer(
    val config: AnalysisConfig
) {
    private companion object {
        const val SLEEP_TIME_WAITING = 15 * 1000L
        const val LOG_FILE_NAME = "work_log.txt"
        const val DONE_LOG_FILE_NAME = "done_log.txt"
    }

    private val runStatuses = listOf(
        SummarizerStatus.NOT_INITIALIZED,
        SummarizerStatus.LOADED,
        SummarizerStatus.RUNNING
    )

    @Volatile var isInterrupted = false
        private set

    private var repoWorkerId = AtomicInteger(0)
    private var dirWorkerId = AtomicInteger(0)

    private val workLogger: WorkLogger
    private val doneLogger: WorkLogger

    private var visitedRepos = mutableListOf<Pair<String?, String?>>()
    private val visitedPaths = mutableSetOf<String>()

    private val parsers = ConcurrentHashMap<Language, SafeParser<out Node>>()

    private val pool = Executors.newFixedThreadPool(config.threadsCount)
    private val workers = LinkedList<Summarizer>()
    private val doneWorkers = LinkedList<Summarizer>()

    init {
        File(config.dataDumpFolder).mkdirs()
        workLogger = WorkLogger(
            File(config.dumpFolder).resolve(LOG_FILE_NAME).absolutePath, isDebug = config.isDebugAnalyzer
        )
        doneLogger = WorkLogger(
            File(config.dumpFolder).resolve(DONE_LOG_FILE_NAME).absolutePath, isDebug = false
        )
        for (lang in Language.values()) {
            parsers[lang] = GumTreeParserFactory.getParser(lang)
        }
        workLogger.add("> ANALYZER THREADS=${config.threadsCount} loaded ${prettyDate(System.currentTimeMillis())}")
        workLogger.add("> DUMP FOLDER ${config.dataDumpFolder}")
    }

    fun submitRepo(analysisRepo: AnalysisRepository): Boolean {
        if (isInterrupted) {
            workLogger.add("CAN'T SUBMIT REPO -- ANALYZER IS INTERRUPTED")
            return false
        }
        if (visitedRepos.contains(Pair(analysisRepo.owner, analysisRepo.name))) {
            workLogger.add("> repo already added $analysisRepo")
            return false
        }
        val id = repoWorkerId.incrementAndGet()
        val worker = analysisRepo.constructRepoSummarizer()
        pool.submit(worker)
        workers.add(worker)
        workLogger.add(
            "> REPO WORKER $id SUBMITTED " +
                "${prettyDate(System.currentTimeMillis())} /${analysisRepo.owner}/${analysisRepo.name}"
        )
        visitedRepos.add(Pair(analysisRepo.owner, analysisRepo.name))
        return true
    }

    fun submitDir(dir: File): Boolean {
        if (isInterrupted) {
            workLogger.add("CAN'T SUBMIT DIR -- ANALYZER IS INTERRUPTED")
            return false
        }
        if (visitedPaths.contains(dir.absolutePath)) {
            workLogger.add("> dir already added ${dir.absolutePath} ")
            return false
        }
        val id = dirWorkerId.incrementAndGet()
        val dumpPath = File(config.dataDumpFolder)
            .resolve("${id}_" + dir.absolutePath.substringAfterLast(File.separator)).absolutePath
        val worker = NoHistorySummarizer(
            analysisPath = dir.absolutePath, dumpPath = dumpPath, parsers = parsers, config = config
        )
        pool.submit(worker)
        workers.add(worker)
        workLogger.add("> DIR WORKER $id SUBMITTED ${prettyDate(System.currentTimeMillis())} ${dir.absolutePath}")
        visitedPaths.add(dir.absolutePath)
        return true
    }

    fun submitAllRepos(repos: List<AnalysisRepository>) = repos.forEach { repo -> submitRepo(repo) }

    fun submitAllDirs(dirs: List<File>) = dirs.forEach { dir -> submitDir(dir) }

    fun isAnyRunning(): Boolean = workers.any { runStatuses.contains(it.status) }

    fun processDoneWorkers() {
        val iter = workers.iterator()
        while (iter.hasNext()) {
            val worker = iter.next()
            if (!runStatuses.contains(worker.status)) {
                doneWorkers.add(worker)
                workLogger.add("> WORKER ${worker.status} ${prettyDate(System.currentTimeMillis())} $worker")
                iter.remove()
            }
        }
        val toDump = mutableListOf<String>()
        while (!doneWorkers.isEmpty()) {
            val worker = doneWorkers.poll()
            toDump.add("${worker.status},$worker")
        }
        doneLogger.addAll(toDump)
        doneLogger.dump()
    }

    fun interrupt() {
        isInterrupted = true
        workers.forEach { worker ->
            worker.status = SummarizerStatus.INTERRUPTED
        }
        pool.shutdown()
        workLogger.dump()
    }

    fun waitUntilAnyRunning() {
        try {
            while (isAnyRunning()) {
                processDoneWorkers()
                Thread.sleep(SLEEP_TIME_WAITING)
            }
            processDoneWorkers()
        } catch (e: InterruptedException) {
            workLogger.add("============ANALYZER WAS INTERRUPTED============")
        } finally {
            interrupt()
        }
    }

    private fun AnalysisRepository.constructRepoSummarizer(): Summarizer {
        val repoDumpPath = this.constructDumpPath(config.dataDumpFolder)
        File(repoDumpPath).mkdirs()
        if (config.isHistoryMode) {
            return HistorySummarizer(analysisRepo = this, dumpPath = repoDumpPath, parsers = parsers, config = config)
        }
        return NoHistorySummarizer(analysisRepo = this, dumpPath = repoDumpPath, parsers = parsers, config = config)
    }
}
