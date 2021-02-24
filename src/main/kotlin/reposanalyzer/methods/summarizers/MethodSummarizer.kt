package reposanalyzer.methods.summarizers

import astminer.common.model.Node
import reposanalyzer.config.Language
import reposanalyzer.methods.MethodSummary
import reposanalyzer.methods.extractors.NodeDataExtractor

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
