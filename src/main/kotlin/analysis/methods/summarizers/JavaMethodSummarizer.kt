package analysis.methods.summarizers

import astminer.common.model.Node
import astminer.common.splitToSubtokens
import analysis.config.Language
import analysis.logic.whichLine
import analysis.methods.MethodSummary
import analysis.methods.buildNormalizedFullName
import analysis.methods.extractors.JavaNodeDataExtractor
import analysis.methods.extractors.getParents
import analysis.methods.normalizeAstLabel

class JavaMethodSummarizer : MethodSummarizer, JavaNodeDataExtractor {

    override val language = Language.JAVA
    override var hideMethodName: Boolean = true
    override var hiddenMethodName: String = MethodSummarizer.DEFAULT_HIDDEN_NAME

    override fun <T : Node> summarize(
        root: T,
        label: String,
        fileContent: String,
        filePath: String,
        fileLinesStarts: List<Int>?
    ): MethodSummary {
        val parents = root.getParents(language)
        val normalizedLabel = normalizeAstLabel(label)
        val splitLabel = splitToSubtokens(label).joinToString("|")
        val normalizedFullName = buildNormalizedFullName(label, parents)
        val doc = root.extractDoc(fileContent)
        val comment = root.extractMultipleComment(fileContent)
        val (pos, length, body) = root.extractBody(label, fileContent, hiddenMethodName)
        val ast = root.extractAST(normalizedLabel)
        return MethodSummary(
            name = normalizedLabel,
            splitName = splitLabel,
            fullName = normalizedFullName,
            argsTypes = root.extractArgsTypes(),
            returnType = root.extractReturnType(),
            filePath = filePath,
            language = language,
            doc = doc,
            comment = comment,
            posInFile = pos,
            length = length,
            firstLineInFile = fileLinesStarts?.whichLine(pos + if (doc != null) 1 else 0),
            lastLineInFile = fileLinesStarts?.whichLine(pos + length - 1),
            body = body,
            ast = ast,
            parents = parents
        )
    }
}
