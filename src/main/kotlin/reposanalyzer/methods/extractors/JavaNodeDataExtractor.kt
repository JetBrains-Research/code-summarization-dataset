package reposanalyzer.methods.extractors

import astminer.common.model.Node
import reposanalyzer.methods.extractContent
import reposanalyzer.methods.summarizers.getChildByTypeLabel
import reposanalyzer.parsing.AstMinerTypeLabels
import reposanalyzer.parsing.getNodeLength
import reposanalyzer.parsing.getNodeStart

interface JavaNodeDataExtractor : NodeDataExtractor {

    override fun <T : Node> T.extractBody(
        label: String,
        fileContent: String,
        hiddenMethodName: String?
    ): Triple<Int, Int, String?> {
        var pos = this.getNodeStart()
        var length = this.getNodeLength()
        this.getChildByTypeLabel(AstMinerTypeLabels.JAVA_DOC)?.let { child ->
            val docLength = child.getNodeLength()
            pos += docLength
            length -= docLength
        }
        var body = extractContent(fileContent, pos, length)
        if (hiddenMethodName != null && body != null) { // replace first label occurrence " $label" to " METHOD_NAME"
            body = body.replaceFirst(" $label", " $hiddenMethodName")
        }
        return Triple(pos, length, body)
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
