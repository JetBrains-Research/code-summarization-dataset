package analysis.granularity.method.extractors.node

import analysis.config.enums.SupportedLanguage
import analysis.granularity.method.buildNormalizedFullName
import analysis.parsing.GumTreeJavaTypeLabels
import analysis.parsing.GumTreePythonTypeLabels
import analysis.parsing.getNodeLabel
import astminer.common.model.Node
import astminer.parse.gumtree.GumTreeNode

fun <T : Node> T.getMethodFullName(label: String, language: SupportedLanguage): String =
    buildNormalizedFullName(label, this.getParents(language))

fun <T : Node> T.getChildByTypeLabel(typeLabel: String): Node? {
    for (child in this.getChildren()) {
        if (child.getTypeLabel() == typeLabel) {
            return child
        }
    }
    return null
}

fun <T : Node> T.getFirstChildByTypeLabelOrNull(typeLabel: String): Node? {
    val child = this.getChildren().firstOrNull() ?: return null
    return if (child.getTypeLabel() == typeLabel) return child else null
}

fun <T : Node> T.getParents(language: SupportedLanguage): List<Pair<String, String>> = when (language) {
    SupportedLanguage.JAVA -> (this as GumTreeNode).getJavaParents()
    SupportedLanguage.PYTHON -> (this as GumTreeNode).getPythonParents()
}

fun GumTreeNode.getJavaParents(): List<Pair<String, String>> {
    val parents = mutableListOf<Pair<String, String>>()
    var currParent = this.getParent()
    while (currParent != null) {
        val child = currParent.getChildByTypeLabel(GumTreeJavaTypeLabels.SIMPLE_NAME)
        if (child != null) {
            val childLabel = child.getNodeLabel()
            parents.add(Pair(childLabel, currParent.getTypeLabel()))
        }
        currParent = currParent.getParent()
    }
    return parents
}

fun GumTreeNode.getPythonParents(): List<Pair<String, String>> {
    val parents = mutableListOf<Pair<String, String>>()
    var currParent = this.getParent()
    while (currParent != null) {
        if (GumTreePythonTypeLabels.PARENTS.contains(currParent.getTypeLabel())) {
            parents.add(Pair(currParent.getNodeLabel(), currParent.getTypeLabel()))
        }
        currParent = currParent.getParent()
    }
    return parents
}
