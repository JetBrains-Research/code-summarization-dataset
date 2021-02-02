package reposanalyzer.methods.extractors

import astminer.common.getNormalizedToken
import astminer.common.model.Node
import astminer.common.preOrder
import astminer.common.storage.RankedIncrementalIdStorage
import reposanalyzer.methods.MethodAST
import reposanalyzer.methods.MethodToken
import reposanalyzer.parsing.AstMinerTypeLabels

interface NodeDataExtractor {

    companion object {
        val TYPES = listOf(
            AstMinerTypeLabels.ARRAY_TYPE,
            AstMinerTypeLabels.PRIMITIVE_TYPE,
            AstMinerTypeLabels.SIMPLE_TYPE,
            AstMinerTypeLabels.PARAMETERIZED_TYPE
        )
    }

    fun <T : Node> extractReturnTypeAndArgs(root: T): Pair<String?, List<String>> =
        Pair(root.extractReturnType(), root.extractArgsTypes())

    fun <T : Node> T.extractBody(
        label: String,
        fileContent: String,
        hiddenMethodName: String? = null
    ): Triple<Int, Int, String?>? = null

    fun <T : Node> T.extractDoc(fileContent: String): String? = null

    fun <T : Node> T.extractMultipleComment(fileContent: String): String? = null

    fun <T : Node> T.extractArgsTypes(): List<String> {
        val types = mutableListOf<String>()
        this.getChildrenOfType(AstMinerTypeLabels.SINGLE_VAR_DECL).forEach { argNode ->
            argNode.getChildren().forEach { type ->
                val typeLabel = type.getTypeLabel()
                if (TYPES.contains(typeLabel)) {
                    types.add(type.getToken())
                }
            }
        }
        return types
    }

    fun <T : Node> T.extractReturnType(): String? {
        var type: String? = null
        for (child in this.getChildren()) {
            if (TYPES.contains(child.getTypeLabel())) {
                type = child.getToken()
                break
            }
        }
        return type
    }

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
