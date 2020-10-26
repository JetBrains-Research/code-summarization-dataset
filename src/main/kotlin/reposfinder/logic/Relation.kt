package reposfinder.logic

import java.time.LocalDate

enum class Relation(val sign: String) {
    LS("<"),
    LE("<="),
    GT(">"),
    GE(">="),
    EQ("=")
}

fun isIntValueGood(rel: Relation, goodValue: Int, ourValue: Int): Boolean {
    return when (rel) {
        Relation.EQ -> ourValue == goodValue
        Relation.LS -> ourValue < goodValue
        Relation.LE -> ourValue <= goodValue
        Relation.GT -> ourValue > goodValue
        Relation.GE -> ourValue >= goodValue
    }
}

fun isIntValueInRange(range: IntRange, ourValue: Int): Boolean {
    return ourValue in range
}

fun isDateGood(rel: Relation, goodDate: LocalDate, ourDate: LocalDate): Boolean {
    return when (rel) {
        Relation.EQ -> ourDate == goodDate
        Relation.LS -> ourDate < goodDate
        Relation.LE -> ourDate <= goodDate
        Relation.GT -> ourDate > goodDate
        Relation.GE -> ourDate >= goodDate
    }
}

fun isDateInRange(range: Pair<LocalDate, LocalDate>, ourValue: LocalDate): Boolean {
    return ourValue >= range.first && ourValue <= range.second
}
