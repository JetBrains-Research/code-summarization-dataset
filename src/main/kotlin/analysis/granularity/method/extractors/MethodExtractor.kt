package analysis.granularity.method.extractors

import analysis.config.enums.SupportedLanguage
import analysis.granularity.method.MethodSummary
import analysis.granularity.method.extractors.node.NodeDataExtractor
import astminer.common.model.Node

interface MethodExtractor : NodeDataExtractor {
    companion object {
        const val DEFAULT_HIDDEN_NAME = "METHOD_NAME"

        fun get(
            language: SupportedLanguage,
            hideMethodName: Boolean,
            hiddenMethodName: String = DEFAULT_HIDDEN_NAME
        ): MethodExtractor {
            val methodSummarizer = when (language) {
                SupportedLanguage.JAVA -> JavaMethodExtractor()
                SupportedLanguage.PYTHON -> PythonMethodExtractor()
            }
            methodSummarizer.hideMethodName = hideMethodName
            methodSummarizer.hiddenMethodName = hiddenMethodName
            return methodSummarizer
        }
    }

    val language: SupportedLanguage
    var hideMethodName: Boolean
    var hiddenMethodName: String

    fun <T : Node> summarize(
        root: T,
        label: String,
        fileContent: String,
        filePath: String,
        fileLinesStarts: List<Int>? = null
    ): MethodSummary
}
