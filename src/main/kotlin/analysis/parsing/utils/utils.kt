package analysis.parsing

import analysis.config.Language
import analysis.granularity.method.extractors.getChildByTypeLabel
import analysis.granularity.method.extractors.getFirstChildByTypeLabelOrNull
import analysis.parsing.utils.toPathContextNormalizedToken
import astminer.common.model.Node
import astminer.common.preOrder
import astminer.parse.gumtree.GumTreeNode
import astminer.paths.PathMiner

fun <T : Node> T.getNodeLabel(): String {
    return when (this) {
        is GumTreeNode -> this.wrappedNode.label
        else -> throw NotImplementedError()
    }
}

fun <T : Node> T.getNodeStart(): Int {
    return when (this) {
        is GumTreeNode -> this.wrappedNode.pos
        else -> throw NotImplementedError()
    }
}

fun <T : Node> T.getNodeLength(): Int {
    return when (this) {
        is GumTreeNode -> this.wrappedNode.length
        else -> throw NotImplementedError()
    }
}

fun <T : Node> T.retrievePaths(pathMiner: PathMiner, maxPaths: Int): List<String> {
    val paths = pathMiner.retrievePaths(this)
    val pathContexts = paths.map { toPathContextNormalizedToken(it) }
        .shuffled()
        .filter { pathContext -> pathContext.startToken.isNotEmpty() && pathContext.endToken.isNotEmpty() }
        .take(maxPaths)
    return pathContexts.map { pathContext ->
        val nodePath = pathContext.orientedNodeTypes.map { node -> node.typeLabel }
        "${pathContext.startToken},${nodePath.joinToString("|")},${pathContext.endToken}"
    }
}

fun <T : Node> T.excludeNodes(lang: Language, excludeNodes: List<String>, excludeDocNode: Boolean) {
    when (lang) {
        Language.PYTHON -> {
            this.excludeNodes(excludeNodes)
            if (excludeDocNode) {
                this.excludePythonDocNode()
            }
        }
        Language.JAVA -> if (excludeDocNode) {
            this.excludeNodes(listOf(GumTreeJavaTypeLabels.JAVA_DOC) + excludeNodes)
        } else {
            this.excludeNodes(excludeNodes)
        }
    }
}

fun <T : Node> T.excludeNodes(typeLabels: List<String>) = this.preOrder().forEach { node ->
    typeLabels.forEach {
        node.removeChildrenOfType(it)
    }
}

fun <T : Node> T.excludePythonDocNode() = this.getChildByTypeLabel(GumTreePythonTypeLabels.BODY)
    ?.getFirstChildByTypeLabelOrNull(GumTreePythonTypeLabels.EXPRESSION)
    ?.let { expressionNode ->
        expressionNode.getChildren().firstOrNull()?.let { maybeDocNode ->
            if (maybeDocNode.getTypeLabel() == GumTreePythonTypeLabels.CONSTANT_STR) {
                (expressionNode.getChildren() as MutableList<Node>).removeFirst()
            }
        }
    }
