package analysis.granularity.method.filter

import analysis.config.enums.SupportedLanguage
import analysis.granularity.method.MethodSummary
import java.util.function.Predicate

class MethodSummaryFilter(val config: MethodSummaryFilterConfig) {

    private val commonPredicates: MutableList<Predicate<MethodSummary>> = mutableListOf()
    private val langPredicates: MutableMap<SupportedLanguage, MutableList<Predicate<MethodSummary>>> = mutableMapOf()

    init {
        commonPredicates.addAll(config.getCommonPredicates())
        langPredicates[SupportedLanguage.JAVA] = config.getJavaPredicates() as MutableList<Predicate<MethodSummary>>
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
