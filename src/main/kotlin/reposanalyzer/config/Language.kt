package reposanalyzer.config

enum class Language(val extensions: List<String>) {
    JAVA(
        listOf(".java")
    ),
    KOTLIN(
        listOf(".kt", ".kts")
    ),
    CSHARP(
        listOf(".cs")
    ),
    PYTHON(
        listOf(".py")
    )
}
