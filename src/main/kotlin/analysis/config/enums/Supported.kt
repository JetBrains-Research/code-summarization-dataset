package analysis.config.enums

enum class SupportedLanguage(
    val label: String,
    val extensions: List<String>
) {
    JAVA(
        "java",
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
    );

    fun pretty(): String {
        return "${this.label} : ${this.languages.map { it.label }}"
    }
}
