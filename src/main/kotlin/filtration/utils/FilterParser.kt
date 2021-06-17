package filtration.utils

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import filtration.config.FilterConfig
import filtration.logic.ReposFilter
import java.io.File

class FilterParser : CliktCommand(treatUnknownOptionsAsArgs = true) {

    private val arguments by argument().multiple()

    private val filterConfigPath: String by option(
        "--fc",
        "--filter-config",
        help = "Filtering config path"
    ).required().validate {
        require(File(it).exists()) {
            fail("search config file doesn't exist $it")
        }
    }

    private val isDebug by option(
        "-d",
        "--fd",
        "--debug",
        help = "Debug mode: log in console"
    ).flag(default = false)

    override fun run() {
        val searchConfig = FilterConfig(configPath = filterConfigPath, isDebug = isDebug)
        val reposFinder = ReposFilter(config = searchConfig)
        reposFinder.run()
    }
}
