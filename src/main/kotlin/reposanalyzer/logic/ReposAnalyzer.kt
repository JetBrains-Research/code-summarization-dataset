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
import reposanalyzer.utils.prettyDate
import java.io.File
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
        SummarizerStatus.NOT_INITIALIZED,
        SummarizerStatus.LOADED,
        SummarizerStatus.RUNNING
    )

    private val doneWorkersLogFile = File(config.dumpFolder).resolve(DONE_LOG_FILE_NAME)

    private var allPatches = mutableListOf<AnalysisRepository>()
    private var goodPatches = mutableListOf<AnalysisRepository>()
    private var badPatches = mutableListOf<AnalysisRepository>()

    private val parsers = ConcurrentHashMap<Language, Parser<out Node>>()

    private val logger: WorkLogger

    private val pool = Executors.newFixedThreadPool(config.threadsCount)
    private val workers = LinkedList<Summarizer>()
    private val doneWorkers = LinkedList<Summarizer>()

    init {
        File(config.dataDumpFolder).mkdirs()
        doneWorkersLogFile.createNewFile()
        doneWorkersLogFile.clearFile()
        logger = WorkLogger(File(config.dumpFolder).resolve(LOG_FILE_NAME).absolutePath, config.isDebugAnalyzer)
        for (lang in Language.values()) {
            parsers[lang] = GumTreeParserFactory.getParser(lang)
        }
        logger.add("> analyzer with ${config.threadsCount} threads loaded ${prettyDate(System.currentTimeMillis())}")
    }

    fun submit(analysisRepo: AnalysisRepository): Boolean {
        val worker = analysisRepo.constructSummarizer()
        pool.submit(worker)
        workers.add(worker)
        logger.add(
            "> worker SUBMITTED ${prettyDate(System.currentTimeMillis())} /${analysisRepo.owner}/${analysisRepo.name}"
        )
        goodPatches.add(analysisRepo)
        return true
    }

    fun submitAllRepos(repos: List<AnalysisRepository>) = repos.forEach { repo -> submit(repo) }

    fun submitAllDirs(dirs: List<File>) = dirs.forEach { dir ->
        val dumpPath = File(config.dataDumpFolder)
            .resolve(dir.absolutePath.substringAfterLast(File.separator)).absolutePath
        val worker = NoHistorySummarizer(
            analysisPath = dir.absolutePath, dumpPath = dumpPath, parsers = parsers, config = config
        )
        pool.submit(worker)
        workers.add(worker)
        logger.add(
            "> worker SUBMITTED ${prettyDate(System.currentTimeMillis())} ${dir.absolutePath}"
        )
    }

    fun isAnyRunning(): Boolean {
        return workers.any { runStatuses.contains(it.status) }
    }

    fun processDoneWorkers() {
        val iter = workers.iterator()
        while (iter.hasNext()) {
            val worker = iter.next()
            if (!runStatuses.contains(worker.status)) {
                doneWorkers.add(worker)
                logger.add("> worker ${worker.status} ${prettyDate(System.currentTimeMillis())} $worker")
                iter.remove()
            }
        }
        val toDump = mutableListOf<String>()
        while (!doneWorkers.isEmpty()) {
            val worker = doneWorkers.poll()
            toDump.add("${worker.status},${worker}")
        }
        doneWorkersLogFile.appendLines(toDump)
    }

    fun interrupt() {
        workers.forEach { worker ->
            worker.status = SummarizerStatus.INTERRUPTED
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

    private fun AnalysisRepository.constructSummarizer(): Summarizer {
        val repoDumpPath = this.constructDumpPath(config.dataDumpFolder)
        File(repoDumpPath).mkdirs()
        if (config.isHistoryMode) {
            return HistorySummarizer(analysisRepo = this, dumpPath = repoDumpPath, parsers = parsers, config = config)
        }
        return NoHistorySummarizer(analysisRepo = this, dumpPath = repoDumpPath, parsers = parsers, config = config)
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
