package reposanalyzer.methods.summarizers

import astminer.common.model.Node
import reposanalyzer.methods.buildNormalizedFullName
import reposanalyzer.parsing.AstMinerTypeLabels
import reposanalyzer.parsing.getNodeLabel

fun <T : Node> T.getMethodFullName(label: String): String =
    buildNormalizedFullName(label, this.getParents())

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

