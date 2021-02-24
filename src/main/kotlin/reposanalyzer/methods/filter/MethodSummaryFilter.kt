package reposanalyzer.methods.filter

import reposanalyzer.config.Language
import reposanalyzer.methods.MethodSummary
import java.util.function.Predicate

class MethodSummaryFilter(val config: MethodSummaryFilterConfig) {

    val commonPredicates: MutableList<Predicate<MethodSummary>> = mutableListOf()
    val langPredicates: MutableMap<Language, MutableList<Predicate<MethodSummary>>> = mutableMapOf()

    init {
        commonPredicates.addAll(config.getCommonPredicates())
        langPredicates[Language.JAVA] = config.getJavaPredicates() as MutableList<Predicate<MethodSummary>>
    }

    fun isSummaryGood(summary: MethodSummary): Boolean {
        var isGood = commonPredicates.isAllPredsGood(summary)
        langPredicates[summary.language]?.let { langPreds ->
            if (langPreds.isNotEmpty()) {
                isGood = isGood && langPreds.isAllPredsGood(summary)
            }
        }
        return isGood
    }

    fun List<Predicate<MethodSummary>>.isAllPredsGood(summary: MethodSummary): Boolean =
        this.all { pred -> pred.test(summary) }
}
