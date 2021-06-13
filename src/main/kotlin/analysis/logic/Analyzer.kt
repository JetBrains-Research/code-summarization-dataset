package analysis.logic

import analysis.config.AnalysisConfig
import analysis.logic.summarizers.FileSummarizer
import analysis.logic.summarizers.HistorySummarizer
import analysis.logic.summarizers.RepoSummarizer
import analysis.utils.FileLogger
import analysis.utils.SoutLogger
import analysis.utils.prettyCurrentDate
import analysis.utils.prettyDate
import java.io.File

class Analyzer(
    private val config: AnalysisConfig
) {
    private companion object {
        const val LOG_FILE_NAME = "work_log.txt"
        const val DONE_LOG_FILE_NAME = "done_log.txt"
    }

    private val soutLogger: SoutLogger
    private val workLogger: FileLogger
    private val doneLogger: FileLogger

    private val workEnv: WorkEnvironment
    private val parseEnv: ParseEnvironment

    private val submittedFiles = mutableSetOf<String>()
    private val submittedRepos = mutableSetOf<Triple<String?, String?, String?>>()

    init {
        File(config.dataDumpFolder).mkdirs()
        soutLogger = SoutLogger()
        workLogger = FileLogger(
            File(config.dumpFolder).resolve(LOG_FILE_NAME).absolutePath,
            isParent = config.isDebugAnalyzer,
            parentLogger = if (config.isDebugAnalyzer) soutLogger else null
        )
        doneLogger = FileLogger(
            File(config.dumpFolder).resolve(DONE_LOG_FILE_NAME).absolutePath,
            isParent = config.isDebugAnalyzer,
            parentLogger = if (config.isDebugAnalyzer) workLogger else null
        )
        workEnv = WorkEnvironment.construct(config.threadsCount, soutLogger, doneLogger)
        parseEnv = ParseEnvironment.construct(config.threadsCount, config.parser, config.languages)
        workLogger.add("ANALYZER LOADED AT ${prettyDate(System.currentTimeMillis())}")
        workLogger.add("ANALYZER THREADS=${config.threadsCount}")
        workLogger.add("ANALYZER PARSERS=${parseEnv.parsers}")
        workLogger.add("ANALYZER DUMP FOLDER ${config.dataDumpFolder}")
    }

    fun submitFiles(dataPaths: List<String>) = dataPaths.forEach { submitFile(it) }

    fun submitFile(dataPath: String): Boolean {
        if (!workEnv.awaitNewWorkers || workEnv.isShutdown) {
            workLogger.add("ANALYZER: can't submit file -- analyzer was shut down")
            return false
        }
        if (submittedFiles.contains(dataPath)) {
            workLogger.add("ANALYZER: path already added: $dataPath")
            return false
        }
        val id = workEnv.incrementWorkersCount()
        val dumpFolder = dataPath.getFileDumpFolder(id, config.dataDumpFolder)
        File(dumpFolder).mkdirs()
        val worker = FileSummarizer(id, dataPath, dumpFolder, config, workEnv, parseEnv)
        workLogger.add("${worker.type} WORKER $id SUBMITTED ${prettyCurrentDate()} $dataPath")
        submittedFiles.add(dataPath)
        workEnv.submitWorker(worker)
        return true
    }

    fun submitRepos(repos: List<AnalysisRepository>) = repos.forEach { submitRepo(it) }

    fun submitRepo(analysisRepo: AnalysisRepository): Boolean {
        if (!workEnv.awaitNewWorkers || workEnv.isShutdown) {
            workLogger.add("ANALYZER: can't submit file -- analyzer was shut down")
            return false
        }
        val repoId = Triple(analysisRepo.owner, analysisRepo.name, analysisRepo.path)
        if (submittedRepos.contains(repoId)) {
            workLogger.add("ANALYZER: repo already added: $analysisRepo")
            return false
        }
        val id = workEnv.incrementWorkersCount()
        val dumpFolder = analysisRepo.getRepoDumpFolder(id, config.dataDumpFolder)
        File(dumpFolder).mkdirs()
        val worker = if (config.isHistoryMode) {
            HistorySummarizer(id, analysisRepo, dumpFolder, config, workEnv, parseEnv)
        } else {
            RepoSummarizer(id, analysisRepo, dumpFolder, config, workEnv, parseEnv)
        }
        workLogger.add("${worker.type} WORKER $id SUBMITTED ${prettyCurrentDate()} ${analysisRepo.toStringNotNull()}")
        submittedRepos.add(repoId)
        workEnv.submitWorker(worker)
        return true
    }

    fun shutdown() {
        workEnv.shutdown()
        parseEnv.shutDown()
        workLogger.add("ANALYZER DONE")
    }

    fun waitUnitAnyRunning() {
        workEnv.waitUntilAnyRunning()
        shutdown()
    }

    fun waitUntilNotDoneAndAfterUntilAnyRunning() {
        workEnv.waitUntilNotDoneAndAfterUntilAnyRunning()
        shutdown()
    }

    fun stopWaitingNewWorkers() {
        workEnv.awaitNewWorkers = false
        workEnv.signalReady()
    }
}
