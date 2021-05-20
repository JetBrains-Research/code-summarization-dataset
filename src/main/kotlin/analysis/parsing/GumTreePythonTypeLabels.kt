package analysis.parsing

object GumTreePythonTypeLabels {
    const val CLASS_DEF = "ClassDef"
    const val FUNCTION_DEF = "FunctionDef"
    const val ASYNC_FUNCTION_DEF = "AsyncFunctionDef"
    const val NAME_LOAD = "Name_Load"
    const val POS_ONLY_ARGS = "posonlyargs"
    const val KW_ONLY_ARGS = "kwonlyargs"
    const val ARGUMENTS = "arguments"
    const val VARARG = "vararg"
    const val KWARG = "kwarg"
    const val ARGS = "args"
    const val ARG = "arg"

    const val BODY = "body"
    const val RETURN = "Return"
    const val PASS = "Pass"
    const val EXPRESSION = "Expr"
    const val CONSTANT_STR = "Constant-str"

    val FUNCTION_ARGS = listOf(
        ARGS,
        POS_ONLY_ARGS,
        KW_ONLY_ARGS
    )

    val PARENTS = listOf(
        CLASS_DEF,
        FUNCTION_DEF,
        ASYNC_FUNCTION_DEF
    )
}
