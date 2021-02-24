package reposanalyzer.methods.summarizers

import astminer.cli.separateToken
import astminer.common.model.Node
import reposanalyzer.config.Language
import reposanalyzer.logic.whichLine
import reposanalyzer.methods.MethodSummary
import reposanalyzer.methods.buildNormalizedFullName
import reposanalyzer.methods.extractors.JavaNodeDataExtractor
import reposanalyzer.methods.extractors.getParents
import reposanalyzer.methods.normalizeAstLabel

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
        val parents = root.getParents()
        val normalizedLabel = normalizeAstLabel(label)
        val splittedLabel = separateToken(label)
        val normalizedFullName = buildNormalizedFullName(label, parents)
        val doc = root.extractDoc(fileContent)
        val comment = root.extractMultipleComment(fileContent)
        val (pos, length, body) = root.extractBody(label, fileContent, hiddenMethodName)
        val ast = root.extractAST(normalizedLabel)
        return MethodSummary(
            name = normalizedLabel,
            splitName = splittedLabel,
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
