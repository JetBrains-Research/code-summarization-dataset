package reposanalyzer.utils

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import reposanalyzer.config.AnalysisConfig
import reposanalyzer.logic.RepoInfo
import reposanalyzer.logic.ReposAnalyzer
import reposanalyzer.logic.loadReposPatches
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

    override fun run() {
        val analysisConfig = AnalysisConfig(configPath = analysisConfigPath, isDebug = true)
        val reposAnalyzer = ReposAnalyzer(config = analysisConfig)
        reposAnalyzer.submitAll(
            loadReposPatches(analysisConfig.reposUrlsPath).map { RepoInfo(it) }
        )
    }
}
