package analysis.config

enum class CommitsType(val label: String) {
    ONLY_MERGES("merges"),
    FIRST_PARENTS_INCLUDE_MERGES("first_parents")
}
