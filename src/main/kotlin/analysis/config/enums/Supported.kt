package analysis.config.enums

enum class SupportedLanguage(
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

enum class SupportedParser(
    val label: String,
    val languages: List<SupportedLanguage>
) {
    GUMTREE(
        "gumtree",
        listOf(SupportedLanguage.JAVA, SupportedLanguage.PYTHON)
    )
}
