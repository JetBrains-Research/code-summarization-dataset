package filtration.logic.filters

import java.time.LocalDate

enum class Relation(val sign: String) {
    LS("<"),
    LE("<="),
    GT(">"),
    GE(">="),
    EQ("=")
}

fun String.isRelation(): Relation? {
    for (rel in Relation.values()) {
        if (this == rel.sign) {
            return rel
        }
    }
    return null
}

fun Relation.isIntValueGood(repoValue: Long, goodValue: Long): Boolean =
    when (this) {
        Relation.EQ -> repoValue == goodValue
        Relation.LS -> repoValue < goodValue
        Relation.LE -> repoValue <= goodValue
        Relation.GT -> repoValue > goodValue
        Relation.GE -> repoValue >= goodValue
    }

fun Relation.isDateGood(repoDate: LocalDate, goodDate: LocalDate): Boolean =
    when (this) {
        Relation.EQ -> repoDate == goodDate
        Relation.LS -> repoDate < goodDate
        Relation.LE -> repoDate <= goodDate
        Relation.GT -> repoDate > goodDate
        Relation.GE -> repoDate >= goodDate
    }

fun LocalDate.isDateInRange(range: Pair<LocalDate, LocalDate>): Boolean =
    this >= range.first && this <= range.second
