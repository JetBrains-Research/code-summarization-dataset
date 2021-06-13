package analysis.granularity.method.extractors

import astminer.common.model.Node
import analysis.granularity.method.extractContent
import analysis.parsing.GumTreePythonTypeLabels
import analysis.parsing.getNodeLength
import analysis.parsing.getNodeStart

interface PythonNodeDataExtractor : NodeDataExtractor {

    private companion object {
        val BAD_ARGS_TYPES_PREF_SUF = listOf(',', ':', ' ', ')', '(', '*', '\\', '=')
    }

    override fun <T : Node> T.extractBody(
        label: String,
        fileContent: String,
        hiddenMethodName: String?
    ): Triple<Int, Int, String?> {
        var pos = this.getNodeStart()
        var length = this.getNodeLength()
        if (pos == 1) { // strange - if method starts with first character of file => its pos == 1, not 0 
            pos = 0
            length++
        }
        var body = extractContent(fileContent, pos, length)
        if (hiddenMethodName != null && body != null) { // replace first label occurrence " $label" to " METHOD_NAME"
            body = body.replaceFirst(" $label", " $hiddenMethodName")
        }
        return Triple(pos, length, body)
    }

    override fun <T : Node> T.extractDoc(fileContent: String): String? {
        var docPos = 0
        var docLength = 0
        this.getChildByTypeLabel(GumTreePythonTypeLabels.BODY)
            ?.getFirstChildByTypeLabelOrNull(GumTreePythonTypeLabels.EXPRESSION)
            ?.getFirstChildByTypeLabelOrNull(GumTreePythonTypeLabels.CONSTANT_STR)?.let { child ->
                docPos = child.getNodeStart()
                docLength = child.getNodeLength()
            }
        return extractContent(fileContent, docPos, docLength)
    }

    override fun <T : Node> extractReturnTypeAndArgs(root: T): Pair<String?, List<String>> =
        Pair(root.extractReturnType(), root.extractArgsTypes())

    override fun <T : Node> T.extractArgsTypes(fileContent: String): List<String> {
        val params = this.getChildrenOfType(GumTreePythonTypeLabels.ARGUMENTS).flatMap {
            it.getChildren()
        }.filter {
            GumTreePythonTypeLabels.FUNCTION_ARGS.contains(it.getTypeLabel())
        }.flatMap {
            it.getChildren()
        }.filter {
            it.getTypeLabel() == GumTreePythonTypeLabels.ARG
        } as MutableList

        this.getChildrenOfType(GumTreePythonTypeLabels.ARGUMENTS).flatMap {
            it.getChildren()
        }.filter {
            it.getTypeLabel() == GumTreePythonTypeLabels.VARARG ||
                it.getTypeLabel() == GumTreePythonTypeLabels.KWARG
        }.forEach {
            params.add(it)
        }

        val withType = params.filter { it.getChildren().isNotEmpty() }
        if (withType.size != params.size) {
            return emptyList()
        }
        return params.mapNotNull { param ->
            val pos = param.getNodeStart()
            val length = param.getNodeLength()
            val tokenLen = param.getToken().length
            extractContent(fileContent, pos + tokenLen, length - tokenLen + 1)?.clearTypeString()
        }
    }

    private fun String.clearTypeString(): String {
        var type = this
        while (type.isNotEmpty() && BAD_ARGS_TYPES_PREF_SUF.contains(type.first())) {
            type = type.removePrefix(type.first().toString())
        }
        while (type.isNotEmpty() && BAD_ARGS_TYPES_PREF_SUF.contains(type.last())) {
            type = type.removeSuffix(type.last().toString())
        }
        return type
    }
}
