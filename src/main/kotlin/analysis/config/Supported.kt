package analysis.config

enum class Language(
    val label: String,
    val extensions: List<String>
) {
    JAVA(
        "Java",
        listOf(".java")
    ),
    PYTHON(
        "python",
        listOf(".py")
    )
}

enum class Parser(
    val label: String,
    val languages: List<Language>
) {
    GUMTREE(
        "gumtree",
        listOf(Language.JAVA, Language.PYTHON)
    )
}
