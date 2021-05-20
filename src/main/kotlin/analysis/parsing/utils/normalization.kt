package analysis.parsing.utils

import astminer.common.DEFAULT_TOKEN
import astminer.common.model.ASTPath
import astminer.common.model.Direction
import astminer.common.model.Node
import astminer.common.model.OrientedNodeType
import astminer.common.model.ParseResult
import astminer.common.model.PathContext
import astminer.common.normalizeToken
import astminer.common.preOrder
import astminer.storage.TokenProcessor

const val NORMALIZED_TOKEN_KEY = "normalized_token"

fun <T : Node> T.getSeparateToken(): String =
    TokenProcessor.Split.getPresentableToken(this).ifEmpty { return DEFAULT_TOKEN }

fun <T : Node> T.getNormalizedToken(): String = metadata[NORMALIZED_TOKEN_KEY]?.toString() ?: DEFAULT_TOKEN

fun <T : Node> normalizeParseResult(parseResult: ParseResult<T>, splitTokens: Boolean) {
    parseResult.root?.preOrder()?.forEach { node -> processNodeToken(node, splitTokens) }
}

fun processNodeToken(node: Node, splitToken: Boolean) {
    if (splitToken) {
        node.setNormalizedToken(node.getSeparateToken())
    } else {
        node.setNormalizedToken()
    }
}

fun Node.setNormalizedToken() {
    this.metadata[NORMALIZED_TOKEN_KEY] = normalizeToken(getToken(), DEFAULT_TOKEN)
}

fun Node.setNormalizedToken(token: String) {
    this.metadata[NORMALIZED_TOKEN_KEY] = token
}

fun toPathContextNormalizedToken(path: ASTPath): PathContext {
    val startToken = normalizeToken(path.upwardNodes.first().getToken(), DEFAULT_TOKEN)
    val endToken = normalizeToken(path.downwardNodes.last().getToken(), DEFAULT_TOKEN)
    val astNodes = path.upwardNodes.map { OrientedNodeType(it.getTypeLabel(), Direction.UP) } +
        OrientedNodeType(path.topNode.getTypeLabel(), Direction.TOP) +
        path.downwardNodes.map { OrientedNodeType(it.getTypeLabel(), Direction.DOWN) }
    return PathContext(startToken, astNodes, endToken)
}
