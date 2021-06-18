package utils

import analysis.utils.AnalyzerParser
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import filtration.utils.FilterParser
import provider.utils.ProviderParser

class AllModesParser(private val args: Array<String>) : CliktCommand(treatUnknownOptionsAsArgs = true) {
    private val arguments by argument().multiple()

    private val modes = listOf("filter", "analysis", "filter-analysis")

    private val mode by option(
        "-m",
        "--mode",
        help = "Select supported mode from $modes"
    ).required().validate {
        require(modes.contains(it)) {
            fail("bad mode")
        }
    }

    override fun run() {
        when (mode) {
            "filter" -> FilterParser().main(args)
            "analysis" -> AnalyzerParser().main(args)
            "filter-analysis" -> ProviderParser().main(args)
        }
    }
}
