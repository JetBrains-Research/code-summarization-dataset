package analysis.parsing

import analysis.methods.extractors.getChildByTypeLabel
import analysis.methods.extractors.getFirstChildByTypeLabelOrNull
import astminer.common.model.Node
import astminer.common.preOrder
import astminer.parse.gumtree.GumTreeNode

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
