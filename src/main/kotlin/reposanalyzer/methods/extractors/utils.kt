package reposanalyzer.methods.extractors

import astminer.common.model.Node
import astminer.parse.java.GumTreeJavaNode
import astminer.parse.python.GumTreePythonNode
import reposanalyzer.config.Language
import reposanalyzer.methods.buildNormalizedFullName
import reposanalyzer.parsing.GumTreeJavaTypeLabels
import reposanalyzer.parsing.GumTreePythonTypeLabels
import reposanalyzer.parsing.getNodeLabel

fun <T : Node> T.getMethodFullName(label: String, language: Language): String =
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

fun <T : Node> T.getParents(language: Language): List<Pair<String, String>> = when (language) {
    Language.JAVA -> (this as GumTreeJavaNode).getParents()
    Language.PYTHON -> (this as GumTreePythonNode).getParents()
}

fun GumTreeJavaNode.getParents(): List<Pair<String, String>> {
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

fun GumTreePythonNode.getParents(): List<Pair<String, String>> {
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
