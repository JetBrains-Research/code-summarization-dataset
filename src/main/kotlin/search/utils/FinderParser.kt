package search.utils

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import search.config.SearchConfig
import search.logic.ReposFinder
import java.io.File

class FinderParser : CliktCommand() {

    private val searchConfigPath: String by option(
        "-s",
        "--search",
        help = "Search config path"
    ).required().validate {
        require(File(it).exists()) {
            fail("search config file doesn't exist $it")
        }
    }

    private val isDebug by option(
        "-d",
        "--debug",
        help = "Debug mode: log in console"
    ).flag(default = false)

    override fun run() {
        val searchConfig = SearchConfig(configPath = searchConfigPath, isDebug = isDebug)
        val reposFinder = ReposFinder(config = searchConfig)
        reposFinder.run()
    }
}
