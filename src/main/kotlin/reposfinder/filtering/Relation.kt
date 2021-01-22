package reposfinder.filtering

enum class Relation(val sign: String) {
    LS("<"),
    LE("<="),
    GT(">"),
    GE(">="),
    EQ("=")
}
