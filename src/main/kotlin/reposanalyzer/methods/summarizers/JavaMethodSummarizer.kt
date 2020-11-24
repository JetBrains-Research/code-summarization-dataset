package reposanalyzer.methods.summarizers

import astminer.common.model.Node
import reposanalyzer.config.Language
import reposanalyzer.methods.MethodSummary
import reposanalyzer.methods.buildNormalizedFullName
import reposanalyzer.methods.extractContent
import reposanalyzer.methods.normalizeAstLabel
import reposanalyzer.parsing.AstMinerTypeLabels
import reposanalyzer.parsing.getNodeLength
import reposanalyzer.parsing.getNodeStart

class JavaMethodSummarizer : MethodSummarizer {

    override val language = Language.JAVA
    override var hideName: Boolean = true
    override var hiddenMethodName: String = MethodSummarizer.DEFAULT_HIDDEN_NAME

    override fun <T : Node> summarize(
        root: T,
        label: String,
        fileContent: String,
        filePath: String
    ): MethodSummary {
        val parents = root.getParents()
        val normalizedLabel = normalizeAstLabel(label)
        val normalizedFullName = buildNormalizedFullName(label, parents)
        val doc = root.extractDoc(fileContent)
        val comment = root.extractMultipleComment(fileContent)
        val body = root.extractBody(label, fileContent)
        val ast = root.extractAST(normalizedLabel)
        return MethodSummary(
            name = normalizedLabel,
            fullName = normalizedFullName,
            filePath = filePath,
            language = language,
            doc = doc,
            comment = comment,
            body = body,
            ast = ast,
            parents = parents
        )
    }

    override fun <T : Node> T.extractBody(label: String, fileContent: String): String? {
        var pos = this.getNodeStart()
        var length = this.getNodeLength()
        this.getChildByTypeLabel(AstMinerTypeLabels.JAVA_DOC)?.let { child ->
            val docLength = child.getNodeLength()
            pos += docLength
            length -= docLength
        }
        var body = extractContent(fileContent, pos, length)
        if (hideName && body != null) { // replace first label occurrence " $label" to " METHOD_NAME"
            body = body.replaceFirst(" $label", " $hiddenMethodName")
        }
        return body
    }

    override fun <T : Node> T.extractDoc(fileContent: String): String? {
        var docPos = 0
        var docLength = 0
        this.getChildByTypeLabel(AstMinerTypeLabels.JAVA_DOC)?.let { child ->
            docPos = child.getNodeStart()
            docLength = child.getNodeLength()
        }
        return extractContent(fileContent, docPos, docLength)
    }

    override fun <T : Node> T.extractMultipleComment(fileContent: String): String? {
        var comment: String? = null
        val pos = this.getNodeStart()
        val fileBeforeMethod = fileContent.substring(0, pos).trimIndent().trimEnd()
        if (fileBeforeMethod.endsWith("*/")) {
            val posJavaDoc = fileBeforeMethod.lastIndexOf("/**")
            val posComment = fileBeforeMethod.lastIndexOf("/*")
            if (posJavaDoc < 0 && posComment > 0) { // it isn't javadoc, just comment
                comment = fileBeforeMethod.substring(posComment)
            }
        }
        return comment
    }
}
