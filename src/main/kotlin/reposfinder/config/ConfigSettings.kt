package reposfinder.config

import reposfinder.api.APIQ
import reposfinder.logic.Relation
import java.lang.IllegalStateException
import java.time.LocalDate

class ConfigSettings {
    // api v3 query
    var v3query = "q="
        private set

    val repoSearch = ReposSearchSettings()

    val EQ = Relation.EQ
    val LS = Relation.LS
    val LE = Relation.LE
    val GT = Relation.GT
    val GE = Relation.GE

    private fun addIntRange(qual: APIQ, range: IntRange) {
        checkLength()
        if (v3query != "q=") {
            v3query += "+"
        }
        v3query += "${qual.type}${range.first}..${range.last}"
    }

    private fun addStringRange(qual: APIQ, pair: Pair<String, String>) {
        checkLength()
        if (v3query != "q=") {
            v3query += "+"
        }
        v3query += "${qual.type}${pair.first}..${pair.second}"
    }

    private fun <T> addValueWithRelation(qual: APIQ, rel: Relation, value: T) {
        checkLength()
        if (!v3query.endsWith("=")) {
            v3query += "+"
        }
        v3query += when (rel) {
            EQ -> "${qual.type}$value"
            else -> "${qual.type}${rel.sign}$value"
        }
    }

    private fun checkLength() {
        if (v3query.length >= 254) {
            throw IllegalStateException("CONFIG: TO MANY PARAMS -- GitHub API query must be no longer 256 symbols!")
        }
    }

    private fun rangeAlreadyAddedEXC(qualifier: String) {
        throw IllegalStateException("CONFIG: $qualifier range already added before value")
    }

    private fun valueAlreadyAddedEXC(qualifier: String) {
        throw IllegalStateException("CONFIG: $qualifier value already added before range")
    }

    fun setUp(settings: ConfigSettings.() -> Unit): ConfigSettings {
        return this.apply(settings)
    }

    fun language(value: String) {
        repoSearch.language = value
        addValueWithRelation(APIQ.LANG, Relation.EQ, value)
    }

    fun organisation(value: String) {
        addValueWithRelation(APIQ.ORG, Relation.EQ, value)
    }

    fun user(value: String) {
        addValueWithRelation(APIQ.USER, Relation.EQ, value)
    }

    fun stars(range: IntRange) { // stars range
        if (repoSearch.starsValue != null) {
            valueAlreadyAddedEXC("Stars")
        }
        repoSearch.starsRange = range
        addIntRange(APIQ.STARS, range)
    }

    fun stars(rel: Relation, value: Int) { // stars value
        if (repoSearch.starsRange != null) {
            rangeAlreadyAddedEXC("Stars")
        }
        repoSearch.starsValue = Pair(rel, value)
        addValueWithRelation(APIQ.STARS, rel, value)
    }

    fun commits(range: IntRange) { // stars range
        if (repoSearch.commitsValue != null) {
            valueAlreadyAddedEXC("Commits")
        }
        repoSearch.commitsRange = range
    }

    fun commits(rel: Relation, value: Int) { // stars value
        if (repoSearch.commitsRange != null) {
            rangeAlreadyAddedEXC("Commits")
        }
        repoSearch.commitsValue = Pair(rel, value)
    }

    fun firstCommit(beg: String, end: String) { // stars range
        if (repoSearch.firstCommitValue != null) {
            valueAlreadyAddedEXC("First commit date")
        }
        repoSearch.firstCommitRange = Pair(LocalDate.parse(beg), LocalDate.parse(end))
    }

    fun firstCommit(rel: Relation, value: String) { // stars value
        if (repoSearch.firstCommitRange != null) {
            rangeAlreadyAddedEXC("First commit date")
        }
        repoSearch.firstCommitValue = Pair(rel, LocalDate.parse(value))
    }

    fun contributors(range: IntRange) { // contibutors range
        if (repoSearch.contributorsValue != null) {
            valueAlreadyAddedEXC("Contributors")
        }
        repoSearch.contributorsRange = range
    }

    fun contributors(rel: Relation, value: Int) { // stars value
        if (repoSearch.contributorsRange != null) {
            rangeAlreadyAddedEXC("Contributors")
        }
        repoSearch.contributorsValue = Pair(rel, value)
    }

    fun forks(range: IntRange) { // forks range
        if (repoSearch.forksValue != null) {
            valueAlreadyAddedEXC("Forks")
        }
        repoSearch.forksRange = range
        addIntRange(APIQ.FORKS, range)
    }

    fun forks(rel: Relation, value: Int) { // forks value
        if (repoSearch.forksRange != null) {
            rangeAlreadyAddedEXC("Forks")
        }
        repoSearch.forksValue = Pair(rel, value)
        addValueWithRelation(APIQ.FORKS, rel, value)
    }

    fun size(range: IntRange) { // size range
        if (repoSearch.sizeValue != null) {
            valueAlreadyAddedEXC("Size")
        }
        repoSearch.sizeRange = range
        addIntRange(APIQ.SIZE, range)
    }

    fun size(rel: Relation, value: Int) { // size value
        if (repoSearch.sizeRange != null) {
            rangeAlreadyAddedEXC("Size")
        }
        repoSearch.sizeValue = Pair(rel, value)
        addValueWithRelation(APIQ.SIZE, rel, value)
    }

    fun followers(range: IntRange) { // followers range
        if (repoSearch.followersValue != null) {
            valueAlreadyAddedEXC("Followers")
        }
        repoSearch.followersRange = range
        addIntRange(APIQ.FOLLOWERS, range)
    }

    fun followers(rel: Relation, value: Int) { // followers value
        if (repoSearch.followersRange != null) {
            rangeAlreadyAddedEXC("Followers")
        }
        repoSearch.followersValue = Pair(rel, value)
        addValueWithRelation(APIQ.FOLLOWERS, rel, value)
    }

    fun created(beg: String, end: String) { // created range
        repoSearch.createdRange = Pair(beg, end)
        addStringRange(APIQ.CREATED, Pair(beg, end))
    }

    fun created(rel: Relation, date: String) { // created value
        repoSearch.createdValue = Pair(rel, date)
        addValueWithRelation(APIQ.CREATED, rel, date)
    }

    fun pushed(beg: String, end: String) { // pushed range
        repoSearch.pushedRange = Pair(beg, end)
        addStringRange(APIQ.PUSH, Pair(beg, end))
    }

    fun pushed(rel: Relation, date: String) { // pushed value
        repoSearch.pushedValue = Pair(rel, date)
        addValueWithRelation(APIQ.PUSH, rel, date)
    }
}
