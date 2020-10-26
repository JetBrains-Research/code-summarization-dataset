package reposfinder.config

import reposfinder.logic.Relation
import java.time.LocalDate

class ReposSearchSettings {
    // repo main language (>=50% of code base) -- v3 API
    var language: String? = null

    // repo owner (organization) -- v3 API
    var organisation: String? = null

    // repo owner (user) -- v3 API
    var user: String? = null

    /*
     *  _Range
     *      Int: X..Y    Date: [beg: YYYY-MM-DD, end: YYYY-MM-DD]
     *
     *  _Value >=, <=, =, >, <
     *         Int: N    Date: YYYY-MM-DD
     */

    // stars count -- v3 API
    var starsRange: IntRange? = null
    var starsValue: Pair<Relation, Int>? = null

    // !default branch commits count -- GraphQL API
    var commitsRange: IntRange? = null
    var commitsValue: Pair<Relation, Int>? = null

    // contributors count -- v3 API pagination hack
    var contributorsRange: IntRange? = null
    var contributorsValue: Pair<Relation, Int>? = null

    // first commit date -- GraphQL API
    var firstCommitRange: Pair<LocalDate, LocalDate>? = null
    var firstCommitValue: Pair<Relation, LocalDate>? = null

    // forks count -- v3 API
    var forksRange: IntRange? = null
    var forksValue: Pair<Relation, Int>? = null

    // followers count -- v3 API
    var followersRange: IntRange? = null
    var followersValue: Pair<Relation, Int>? = null

    // repo size in KB -- v3 API
    var sizeRange: IntRange? = null
    var sizeValue: Pair<Relation, Int>? = null

    // repo created date -- v3 API
    var createdRange: Pair<String, String>? = null
    var createdValue: Pair<Relation, String>? = null

    // repo last updated date -- v3 API
    var pushedRange: Pair<String, String>? = null
    var pushedValue: Pair<Relation, String>? = null
}
