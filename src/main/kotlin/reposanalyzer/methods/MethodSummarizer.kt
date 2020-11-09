package reposanalyzer.methods

import astminer.common.getNormalizedToken
import astminer.common.model.Node
import astminer.common.preOrder
import astminer.common.storage.RankedIncrementalIdStorage
import astminer.parse.java.GumTreeJavaNode
import reposanalyzer.config.Language
import sun.reflect.generics.reflectiveObjects.NotImplementedException

class MethodSummarizer(
    private val hideNames: Boolean,
    private val hiddenMethodName: String = "METHOD_NAME"
) {
    private object TypeLabels {
        const val SIMPLE_NAME = "SimpleName"
        const val JAVA_DOC = "Javadoc"
    }

    fun <T : Node> summarize(
        root: T,
        label: String,
        filePath: String,
        fileContent: String,
        language: Language
    ): MethodSummary {
        val parents = getParents(root)
        val normalizedLabel = normalizeAstLabel(label)
        val normalizedFullName = buildNormalizedFullName(label, parents)
        val body = extractBody(label, root, fileContent, language)
        val doc = extractDoc(root, fileContent, language)
        val comment = extractComment(root, fileContent, language)
        val ast = extractAST(root, normalizedLabel)
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
        val parents = getParents(root)
        return buildNormalizedFullName(label, parents)
    }

    private fun extractBody(label: String, root: Node, fileContent: String, language: Language): String? {
        var pos = getNodeStart(root)
        var length = getNodeLength(root)
        when (language) {
            Language.JAVA -> getJavadocChild(root)?.let { child ->
                val docLength = getNodeLength(child)
                pos += docLength
                length -= docLength
            }
            else -> {} // TODO
        }
        val body = extractContent(fileContent, pos, length)
        // replace first label occurrence " label" to " METHOD_NAME"
        if (hideNames && body != null) {
            return body.replaceFirst(" $label", " $hiddenMethodName")
        }
        return body
    }

    private fun extractDoc(root: Node, fileContent: String, language: Language): String? {
        var docPos = 0
        var docLength = 0
        when (language) {
            Language.JAVA -> getJavadocChild(root)?.let { child ->
                docPos = getNodeStart(child)
                docLength = getNodeLength(child)
            }
            else -> {} // TODO
        }
        return extractContent(fileContent, docPos, docLength)
    }

    private fun extractComment(root: Node, fileContent: String, language: Language): String? {
        val pos = getNodeStart(root)
        when (language) {
            Language.JAVA -> {
                val fileBeforeMethod = fileContent.substring(0, pos).trimIndent().trimEnd()
                if (!fileBeforeMethod.endsWith("*/")) {
                    return null
                }
                val posJavaDoc = fileBeforeMethod.lastIndexOf("/**")
                val posComment = fileBeforeMethod.lastIndexOf("/*")
                if (posJavaDoc < 0 && posComment > 0) { // it isn't javadoc, just comment
                    return fileBeforeMethod.substring(posComment)
                }
            }
            else -> {} // TODO
        }
        return null
    }

    private fun extractContent(fileContent: String, pos: Int, length: Int): String? {
        if (length != 0 && pos + length < fileContent.length) {
            return fileContent.substring(pos, pos + length).trimStart().trimEnd()
        }
        return null
    }

    private fun extractAST(root: Node, normalizedLabel: String): MethodAST {
        val graph = mutableMapOf<Long, List<Long>>()
        val nodesMap = getASTGraph(root, graph)
        val description = mutableListOf<MethodToken>()
        for (node in root.preOrder()) {
            val id = nodesMap.getId(node) - 1
            val token = node.getNormalizedToken()
            val type = node.getTypeLabel()
            description.add(MethodToken(id, token, type))
        }
        // method name hiding
        val sortedDescription = description.sortedBy { it.id }
        if (hideNames) {
            for (token in sortedDescription) {
                if (token.type == TypeLabels.SIMPLE_NAME) {
                    token.name = hiddenMethodName
                    break
                }
            }
        }
        return MethodAST(normalizedLabel, graph, sortedDescription)
    }

    private fun getASTGraph(root: Node, graph: MutableMap<Long, List<Long>>): RankedIncrementalIdStorage<Node> {
        val nodesMap = RankedIncrementalIdStorage<Node>()
        for (node in root.preOrder()) {
            val id = nodesMap.record(node) - 1
            val childrenIds = node.getChildren().map { nodesMap.record(it) - 1 }
            graph[id] = childrenIds
        }
        return nodesMap
    }

    private fun buildNormalizedFullName(label: String, parents: List<Pair<String, String>>? = null): String {
        val normalizedLabel = normalizeAstLabel(label)
        if (parents == null || parents.isEmpty()) {
            return normalizedLabel
        }
        return parents.joinToString(separator = ".", postfix = ".") { (label, _) ->
            normalizeAstLabel(label)
        } + normalizedLabel
    }

    private fun normalizeAstLabel(label: String): String =
        label.replace("[^A-z^0-9^_]".toRegex(), "_")

    private fun <T : Node> getParents(methodRoot: T): List<Pair<String, String>> {
        val parents = mutableListOf<Pair<String, String>>()
        var currParent = methodRoot.getParent()
        while (currParent != null) {
            currParent.getChildren().forEach { child ->
                if (child.getTypeLabel() == TypeLabels.SIMPLE_NAME) {
                    getNodeLabel(child).let { childLabel ->
                        parents.add(Pair(childLabel, currParent!!.getTypeLabel()))
                    }
                    return@forEach
                }
            }
            currParent = currParent.getParent()
        }
        return parents
    }

    private fun <T : Node> getJavadocChild(root: T): Node? {
        for (child in root.getChildren()) {
            if (child.getTypeLabel() == TypeLabels.JAVA_DOC) {
                return child
            }
        }
        return null
    }

    // generic for other GumTreeNodes languages
    private fun <T : Node> getNodeLabel(node: T): String {
        return when (node) {
            is GumTreeJavaNode -> node.wrappedNode.label
            else -> throw NotImplementedException()
        }
    }

    // generic for other GumTreeNodes languages
    private fun <T : Node> getNodeStart(node: T): Int {
        return when (node) {
            is GumTreeJavaNode -> node.wrappedNode.pos
            else -> throw NotImplementedException()
        }
    }

    // generic for other GumTreeNodes languages
    private fun <T : Node> getNodeLength(node: T): Int {
        return when (node) {
            is GumTreeJavaNode -> node.wrappedNode.length
            else -> throw NotImplementedException()
        }
    }
}
