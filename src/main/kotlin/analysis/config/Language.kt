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
