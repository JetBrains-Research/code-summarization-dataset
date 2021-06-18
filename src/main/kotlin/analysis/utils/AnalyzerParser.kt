package analysis.utils

import analysis.config.AnalysisConfig
import analysis.config.loadPaths
import analysis.config.parseRepoUrls
import analysis.git.AnalysisRepository
import analysis.logic.Analyzer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import utils.loadJSONList
import java.io.File

class AnalyzerParser : CliktCommand(treatUnknownOptionsAsArgs = true) {

    private val arguments by argument().multiple()

    private val analysisConfigPath: String by option(
        "--ac",
        "--analysis-config",
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
        "--ad",
        "--analysis-debug",
        help = "Debug mode: analysis log in console"
    ).flag(default = false)

    private val isWorkersDebug by option(
        "--wd",
        "--workers-debug",
        help = "Debug mode: analysis workers log in console"
    ).flag(default = false)

    override fun run() {
        val analysisConfig = AnalysisConfig(
            configPath = analysisConfigPath,
            isDebugAnalyzer = isDebug || isAnalyserDebug,
            isDebugWorkers = isDebug || isWorkersDebug
        )
        val reposUrls = loadJSONList(analysisConfig.reposUrlsPath).parseRepoUrls()
        val dirsPaths = loadPaths(analysisConfig.filesListPath)

        val reposAnalyzer = Analyzer(config = analysisConfig)
        reposAnalyzer.submitRepos(reposUrls.map { AnalysisRepository(owner = it.first, name = it.second) })
        reposAnalyzer.submitFiles(dirsPaths)

        reposAnalyzer.waitUnitAnyRunning()
    }
}
