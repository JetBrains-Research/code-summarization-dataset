package analysis.granularity

import analysis.config.AnalysisConfig
import analysis.config.enums.SupportedLanguage
import analysis.config.enums.SupportedParser
import analysis.config.enums.Task
import analysis.granularity.method.MethodParseProvider
import analysis.logic.CommonInfo
import analysis.logic.ParseEnvironment
import astminer.cli.LabeledResult
import astminer.common.model.Node
import utils.Logger
import java.io.File

interface ParseProvider {
    companion object {
        fun get(config: AnalysisConfig, parseEnv: ParseEnvironment): ParseProvider {
            return when (config.task) {
                Task.NAME -> MethodParseProvider(config, parseEnv)
            }
        }
    }

    fun parse(
        files: List<File>,
        parser: SupportedParser,
        lang: SupportedLanguage
    ): List<ParseResult>

    fun processParseResults(
        parseResults: List<ParseResult>,
        storage: SummaryStorage,
        lang: SupportedLanguage,
        commonInfo: CommonInfo
    )
}

data class ParseResult(
    val filePath: String,
    val result: List<LabeledResult<out Node>> = emptyList(),
    val exception: Exception? = null
) {
    fun logException(logger: Logger?) = exception?.let {
        logger?.add("parse exception for file: $filePath")
        logger?.add(it.stackTraceToString())
    }
}
