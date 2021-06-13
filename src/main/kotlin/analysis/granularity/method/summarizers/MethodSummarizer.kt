package analysis.granularity.method.summarizers

import analysis.config.Language
import analysis.granularity.method.MethodSummary
import analysis.granularity.method.extractors.NodeDataExtractor
import astminer.common.model.Node

interface MethodSummarizer : NodeDataExtractor {
    companion object {
        const val DEFAULT_HIDDEN_NAME = "METHOD_NAME"
    }

    val language: Language
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
