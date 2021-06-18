package provider.utils

import analysis.config.AnalysisConfig
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import filtration.config.FilterConfig
import provider.logic.FilterAnalyserProvider
import java.io.File

class ProviderParser : CliktCommand(treatUnknownOptionsAsArgs = true) {

    private val arguments by argument().multiple()

    private val filtrationConfigPath: String by option(
        "--fc",
        "--filter-config",
        help = "Filter config path"
    ).required().validate {
        require(File(it).exists()) {
            fail("filtration config file doesn't exist $it")
        }
        require(it != analysisConfigPath) {
            fail("analysis and filtration config path must be different")
        }
    }

    private val analysisConfigPath: String by option(
        "--ac",
        "--analysis-config",
        help = "Analysis config path"
    ).required().validate {
        require(File(it).exists()) {
            fail("analysis config file doesn't exist $it")
        }
        require(it != filtrationConfigPath) {
            fail("analysis and filtration config path must be different")
        }
    }

    private val isFullDebug by option(
        "-d",
        "--debug",
        help = "Debug mode: full log in console"
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

    private val isSearchDebug by option(
        "--fd",
        "--filter-debug",
        help = "Debug mode: filtration log in console"
    ).flag(default = false)

    override fun run() {
        val filterConfig = FilterConfig(configPath = filtrationConfigPath, isDebug = isFullDebug || isSearchDebug)
        val analysisConfig = AnalysisConfig(
            configPath = analysisConfigPath,
            isDebugAnalyzer = isFullDebug || isAnalyserDebug,
            isDebugWorkers = isFullDebug || isWorkersDebug
        )
        val provider = FilterAnalyserProvider(filterConfig = filterConfig, analysisConfig = analysisConfig)
        provider.run()
    }
}
