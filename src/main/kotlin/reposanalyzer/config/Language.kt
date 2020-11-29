package reposanalyzer.config

enum class Language(
    val label: String,
    val extensions: List<String>
) {
    JAVA(
        "Java",
        listOf(".java")
    ),
    KOTLIN(
        "Kotlin",
        listOf(".kt", ".kts")
    ),
    CSHARP(
        "C#",
        listOf(".cs")
    ),
    PYTHON(
        "Python",
        listOf(".py")
    ),
    CPP(
        "C++",
        listOf(".cpp", ".c", ".hpp", ".h")
    )
}
