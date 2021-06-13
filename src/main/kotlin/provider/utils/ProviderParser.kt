package provider.utils

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import analysis.config.AnalysisConfig
import search.config.SearchConfig
import provider.logic.SearchAnalysisProvider
import java.io.File

class ProviderParser : CliktCommand() {

    private val searchConfigPath: String by option(
        "-s",
        "--search",
        help = "Search config path"
    ).required().validate {
        require(File(it).exists()) {
            fail("search config file doesn't exist $it")
        }
        require(it != analysisConfigPath) {
            fail("analysis and search config path must be different")
        }
    }

    private val analysisConfigPath: String by option(
        "-a",
        "--analysis",
        help = "Analysis config path"
    ).required().validate {
        require(File(it).exists()) {
            fail("analysis config file doesn't exist $it")
        }
        require(it != searchConfigPath) {
            fail("analysis and search config path must be different")
        }
    }

    private val isFullDebug by option(
        "-d",
        "--debug",
        help = "Debug mode: full log in console"
    ).flag(default = false)

    private val isAnalyserDebug by option(
        "--analysis-debug",
        help = "Debug mode: analysis log in console"
    ).flag(default = false)

    private val isSummarizersDebug by option(
        "--summary-debug",
        help = "Debug mode: analysis log in console"
    ).flag(default = false)

    private val isSearchDebug by option(
        "--search-debug",
        help = "Debug mode: search log in console"
    ).flag(default = false)

    override fun run() {
        val searchConfig = SearchConfig(configPath = searchConfigPath, isDebug = isFullDebug || isSearchDebug)
        val analysisConfig = AnalysisConfig(
            configPath = analysisConfigPath,
            isDebugAnalyzer = isFullDebug || isAnalyserDebug,
            isDebugWorkers = isFullDebug || isSummarizersDebug
        )
        val provider = SearchAnalysisProvider(searchConfig = searchConfig, analysisConfig = analysisConfig)
        provider.run()
    }
}
