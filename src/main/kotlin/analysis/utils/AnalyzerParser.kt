package analysis.utils

import analysis.config.AnalysisConfig
import analysis.config.loadJSONList
import analysis.config.loadPaths
import analysis.config.parseRepoUrls
import analysis.logic.AnalysisRepository
import analysis.logic.Analyzer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import java.io.File

class AnalyzerParser : CliktCommand() {

    private val analysisConfigPath: String by option(
        "-a",
        "--analysis",
        help = "Analysis config path"
    ).required().validate {
        require(File(it).exists()) {
            fail("analysis config file doesn't exist $it")
        }
    }

    private val isDebug by option(
        "-d",
        "--debug",
        help = "Debug mode: log in console"
    ).flag(default = false)

    private val isAnalyserDebug by option(
        "--analysis-debug",
        help = "Debug mode: analysis log in console"
    ).flag(default = false)

    private val isSummarizersDebug by option(
        "--summary-debug",
        help = "Debug mode: analysis log in console"
    ).flag(default = false)

    override fun run() {
        val analysisConfig = AnalysisConfig(
            configPath = analysisConfigPath,
            isDebugAnalyzer = isDebug || isAnalyserDebug,
            isDebugWorkers = isDebug || isSummarizersDebug
        )
        val reposUrls = loadJSONList(analysisConfig.reposUrlsPath).parseRepoUrls()
        val dirsPaths = loadPaths(analysisConfig.dirsListPath)

        val reposAnalyzer = Analyzer(config = analysisConfig)
        reposAnalyzer.submitRepos(reposUrls.map { AnalysisRepository(owner = it.first, name = it.second) })
        reposAnalyzer.submitFiles(dirsPaths)

        reposAnalyzer.waitUnitAnyRunning()
    }
}
