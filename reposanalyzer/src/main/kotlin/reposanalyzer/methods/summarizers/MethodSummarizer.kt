package reposanalyzer.methods.summarizers

import astminer.common.getNormalizedToken
import astminer.common.model.Node
import astminer.common.preOrder
import astminer.common.storage.RankedIncrementalIdStorage
import reposanalyzer.config.Language
import reposanalyzer.methods.MethodAST
import reposanalyzer.methods.MethodSummary
import reposanalyzer.methods.MethodToken
import reposanalyzer.methods.buildNormalizedFullName
import reposanalyzer.parsing.AstMinerTypeLabels
import reposanalyzer.parsing.getNodeLabel

interface MethodSummarizer {
    companion object {
        const val DEFAULT_HIDDEN_NAME = "METHOD_NAME"
    }

    val language: Language
    var hideMethodName: Boolean
    var hiddenMethodName: String

    fun <T : Node> summarize(root: T, label: String, fileContent: String, filePath: String): MethodSummary

    fun <T : Node> T.extractBody(label: String, fileContent: String): String? = null

    fun <T : Node> T.extractDoc(fileContent: String): String? = null

    fun <T : Node> T.extractMultipleComment(fileContent: String): String? = null

    fun <T : Node> T.extractAST(normalizedLabel: String): MethodAST {
        val graph = mutableMapOf<Long, List<Long>>()
        val nodesMap = this.getAST(graph)
        val description = mutableListOf<MethodToken>()
        for (node in this.preOrder()) { // from astminer sources
            val id = nodesMap.getId(node) - 1
            val token = node.getNormalizedToken()
            val type = node.getTypeLabel()
            description.add(MethodToken(id, token, type))
        }
        val sortedDescription = description.sortedBy { it.id }
        return MethodAST(normalizedLabel, graph, sortedDescription)
    }

    fun <T : Node> T.getAST(graph: MutableMap<Long, List<Long>>): RankedIncrementalIdStorage<Node> {
        val nodesMap = RankedIncrementalIdStorage<Node>()
        for (node in this.preOrder()) { // from astminer sources
            val id = nodesMap.record(node) - 1
            val childrenIds = node.getChildren().map { nodesMap.record(it) - 1 }
            graph[id] = childrenIds
        }
        return nodesMap
    }

    fun <T : Node> getMethodFullName(root: T, label: String): String =
        buildNormalizedFullName(label, root.getParents())

    fun <T : Node> T.getChildByTypeLabel(typeLabel: String): Node? {
        for (child in this.getChildren()) {
            if (child.getTypeLabel() == typeLabel) {
                return child
            }
        }
        return null
    }

    fun <T : Node> T.getParents(): List<Pair<String, String>> {
        val parents = mutableListOf<Pair<String, String>>()
        var currParent = this.getParent()
        while (currParent != null) {
            val child = currParent.getChildByTypeLabel(AstMinerTypeLabels.SIMPLE_NAME)
            if (child != null) {
                val childLabel = child.getNodeLabel()
                parents.add(Pair(childLabel, currParent.getTypeLabel()))
            }
            currParent = currParent.getParent()
        }
        return parents
    }
}
