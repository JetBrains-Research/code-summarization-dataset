package reposanalyzer.methods.extractors

import astminer.common.getNormalizedToken
import astminer.common.model.Node
import astminer.common.preOrder
import astminer.common.storage.RankedIncrementalIdStorage
import reposanalyzer.methods.MethodAST
import reposanalyzer.methods.MethodToken

interface NodeDataExtractor {

    fun <T : Node> T.extractBody(
        label: String,
        fileContent: String,
        hiddenMethodName: String? = null
    ): Triple<Int, Int, String?>? = null

    fun <T : Node> T.extractDoc(fileContent: String): String? = null

    fun <T : Node> T.extractMultipleComment(fileContent: String): String? = null

    fun <T : Node> T.extractArgsTypes(): List<String> = emptyList()

    fun <T : Node> T.extractArgsTypes(fileContent: String): List<String> = emptyList()

    fun <T : Node> T.extractReturnType(): String? = null

    fun <T : Node> extractReturnTypeAndArgs(root: T): Pair<String?, List<String>> =
        Pair(root.extractReturnType(), root.extractArgsTypes())

    fun <T : Node> extractReturnTypeAndArgs(root: T, fileContent: String): Pair<String?, List<String>> =
        Pair(root.extractReturnType(), root.extractArgsTypes(fileContent))

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
}
