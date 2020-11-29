package reposanalyzer.parsing

import astminer.common.model.Node
import astminer.parse.java.GumTreeJavaNode

/*
 * generic for other GumTreeNodes languages
 */
fun <T : Node> T.getNodeLabel(): String {
    return when (this) {
        is GumTreeJavaNode -> this.wrappedNode.label
        else -> throw NotImplementedError() // TODO
    }
}

fun <T : Node> T.getNodeStart(): Int {
    return when (this) {
        is GumTreeJavaNode -> this.wrappedNode.pos
        else -> throw NotImplementedError() // TODO
    }
}

fun <T : Node> T.getNodeLength(): Int {
    return when (this) {
        is GumTreeJavaNode -> this.wrappedNode.length
        else -> throw NotImplementedError() // TODO
    }
}
