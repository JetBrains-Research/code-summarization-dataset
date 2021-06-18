package analysis.granularity.method.extractors.node

import analysis.granularity.method.extractContent
import analysis.parsing.GumTreeJavaTypeLabels
import analysis.parsing.getNodeLength
import analysis.parsing.getNodeStart
import astminer.common.model.Node

interface JavaNodeDataExtractor : NodeDataExtractor {

    companion object {
        val TYPES = listOf(
            GumTreeJavaTypeLabels.ARRAY_TYPE,
            GumTreeJavaTypeLabels.PRIMITIVE_TYPE,
            GumTreeJavaTypeLabels.SIMPLE_TYPE,
            GumTreeJavaTypeLabels.PARAMETERIZED_TYPE
        )
    }

    override fun <T : Node> T.extractBody(
        label: String,
        fileContent: String,
        hiddenMethodName: String?
    ): Triple<Int, Int, String?> {
        var pos = this.getNodeStart()
        var length = this.getNodeLength()
        this.getChildByTypeLabel(GumTreeJavaTypeLabels.JAVA_DOC)?.let { child ->
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
        this.getChildByTypeLabel(GumTreeJavaTypeLabels.JAVA_DOC)?.let { child ->
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

    override fun <T : Node> T.extractArgsTypes(fileContent: String): List<String> = this.extractArgsTypes()

    override fun <T : Node> T.extractArgsTypes(): List<String> {
        val types = mutableListOf<String>()
        this.getChildrenOfType(GumTreeJavaTypeLabels.SINGLE_VAR_DECL).forEach { argNode ->
            argNode.getChildren().forEach { type ->
                val typeLabel = type.getTypeLabel()
                if (TYPES.contains(typeLabel)) {
                    types.add(type.getToken())
                }
            }
        }
        return types
    }

    override fun <T : Node> T.extractReturnType(): String? {
        var type: String? = null
        for (child in this.getChildren()) {
            if (TYPES.contains(child.getTypeLabel())) {
                type = child.getToken()
                break
            }
        }
        return type
    }
}
