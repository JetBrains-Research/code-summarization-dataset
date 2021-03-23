package reposanalyzer.parsing

import astminer.common.getNormalizedToken
import astminer.common.model.ASTPath
import astminer.common.model.Direction
import astminer.common.model.Node
import astminer.common.model.OrientedNodeType
import astminer.common.model.PathContext
import astminer.parse.java.GumTreeJavaNode
import astminer.parse.python.GumTreePythonNode

/*
 * generic for other GumTreeNodes languages
 */
fun <T : Node> T.getNodeLabel(): String {
    return when (this) {
        is GumTreeJavaNode -> this.wrappedNode.label
        is GumTreePythonNode -> this.wrappedNode.label
        else -> throw NotImplementedError() // TODO
    }
}

fun <T : Node> T.getNodeStart(): Int {
    return when (this) {
        is GumTreeJavaNode -> this.wrappedNode.pos
        is GumTreePythonNode -> this.wrappedNode.pos
        else -> throw NotImplementedError() // TODO
    }
}

fun <T : Node> T.getNodeLength(): Int {
    return when (this) {
        is GumTreeJavaNode -> this.wrappedNode.length
        is GumTreePythonNode -> this.wrappedNode.length
        else -> throw NotImplementedError() // TODO
    }
}

fun toPathContextNormalizedToken(path: ASTPath): PathContext {
    val startToken = path.upwardNodes.first().getNormalizedToken()
    val endToken = path.downwardNodes.last().getNormalizedToken()
    val astNodes = path.upwardNodes.map { OrientedNodeType(it.getTypeLabel(), Direction.UP) } +
        OrientedNodeType(path.topNode.getTypeLabel(), Direction.TOP) +
        path.downwardNodes.map { OrientedNodeType(it.getTypeLabel(), Direction.DOWN) }
    return PathContext(startToken, astNodes, endToken)
}
