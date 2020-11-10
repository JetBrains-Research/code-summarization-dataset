package reposanalyzer.methods

import astminer.common.getNormalizedToken
import astminer.common.model.Node
import astminer.common.preOrder
import astminer.common.storage.RankedIncrementalIdStorage
import reposanalyzer.config.Language
import reposanalyzer.parsing.getChildByTypeLabel
import reposanalyzer.parsing.getNodeLabel
import reposanalyzer.parsing.getNodeLength
import reposanalyzer.parsing.getNodeStart
import reposanalyzer.methods.normalizeAstLabel

class MethodSummarizer(
    private val hideNames: Boolean = true,
    private val hiddenMethodName: String = "METHOD_NAME"
) {
    private object TypeLabels {
        const val SIMPLE_NAME = "SimpleName"
        const val JAVA_DOC = "Javadoc"
    }

    fun <T : Node> summarize(
        root: T,
        label: String,
        fileContent: String,
        filePath: String,
        language: Language
    ): MethodSummary {
        val parents = root.getParents()
        val normalizedLabel = normalizeAstLabel(label)
        val normalizedFullName = buildNormalizedFullName(label, parents)
        val doc = root.extractDoc(fileContent, language)
        val comment = root.extractComment(fileContent, language)
        val body = root.extractBody(label, fileContent, language)
        val ast = root.extractAST(normalizedLabel)
        return MethodSummary(
            name = normalizedLabel,
            fullName = normalizedFullName,
            filePath = filePath,
            doc = doc,
            comment = comment,
            body = body,
            ast = ast,
            parents = parents
        )
    }

    fun <T : Node> getMethodFullName(root: T, label: String): String {
        val parents = root.getParents()
        return buildNormalizedFullName(label, parents)
    }

    private fun <T : Node> T.extractBody(label: String, fileContent: String, language: Language): String? {
        var pos = this.getNodeStart()
        var length = this.getNodeLength()
        when (language) {
            Language.JAVA -> this.getChildByTypeLabel(TypeLabels.JAVA_DOC)?.let { child ->
                val docLength = child.getNodeLength()
                pos += docLength
                length -= docLength
            }
            else -> throw NotImplementedError() // TODO
        }
        var body = extractContent(fileContent, pos, length)
        // replace first label occurrence " $label" to " METHOD_NAME"
        if (hideNames && body != null) {
            body = body.replaceFirst(" $label", " $hiddenMethodName")
        }
        return body
    }

    private fun <T : Node> T.extractDoc(fileContent: String, language: Language): String? {
        var docPos = 0
        var docLength = 0
        when (language) {
            Language.JAVA -> this.getChildByTypeLabel(TypeLabels.JAVA_DOC)?.let { child ->
                docPos = child.getNodeStart()
                docLength = child.getNodeLength()
            }
            else -> throw NotImplementedError() // TODO
        }
        return extractContent(fileContent, docPos, docLength)
    }

    private fun <T : Node> T.extractComment(fileContent: String, language: Language): String? {
        val pos = this.getNodeStart()
        var comment: String? = null
        when (language) {
            Language.JAVA -> {
                val fileBeforeMethod = fileContent.substring(0, pos).trimIndent().trimEnd()
                if (fileBeforeMethod.endsWith("*/")) {
                    val posJavaDoc = fileBeforeMethod.lastIndexOf("/**")
                    val posComment = fileBeforeMethod.lastIndexOf("/*")
                    if (posJavaDoc < 0 && posComment > 0) { // it isn't javadoc, just comment
                        comment = fileBeforeMethod.substring(posComment)
                    }
                }
            }
            else -> throw NotImplementedError() // TODO
        }
        return comment
    }

    private fun <T : Node> T.extractAST(normalizedLabel: String): MethodAST {
        val graph = mutableMapOf<Long, List<Long>>()
        val nodesMap = this.getAST(graph)
        val description = mutableListOf<MethodToken>()
        for (node in this.preOrder()) { // from astminer sources
            val id = nodesMap.getId(node) - 1
            val token = node.getNormalizedToken()
            val type = node.getTypeLabel()
            description.add(MethodToken(id, token, type))
        }
        // method name hiding
        val sortedDescription = description.sortedBy { it.id }
        if (hideNames) {
            for (token in sortedDescription) {
                if (token.type == TypeLabels.SIMPLE_NAME) { // first SimpleName token is method name
                    token.name = hiddenMethodName
                    break
                }
            }
        }
        return MethodAST(normalizedLabel, graph, sortedDescription)
    }

    private fun <T : Node> T.getAST(graph: MutableMap<Long, List<Long>>): RankedIncrementalIdStorage<Node> {
        val nodesMap = RankedIncrementalIdStorage<Node>()
        for (node in this.preOrder()) { // from astminer sources
            val id = nodesMap.record(node) - 1
            val childrenIds = node.getChildren().map { nodesMap.record(it) - 1 }
            graph[id] = childrenIds
        }
        return nodesMap
    }

    private fun <T : Node> T.getParents(): List<Pair<String, String>> {
        val parents = mutableListOf<Pair<String, String>>()
        var currParent = this.getParent()
        while (currParent != null) {
            val child = currParent.getChildByTypeLabel(TypeLabels.SIMPLE_NAME)
            if (child != null) {
                val childLabel = child.getNodeLabel()
                parents.add(Pair(childLabel, currParent.getTypeLabel()))
            }
            currParent = currParent.getParent()
        }
        return parents
    }
}
