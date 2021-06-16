package analysis.granularity.method.filter

import analysis.granularity.method.MethodSummary
import analysis.granularity.method.filter.predicates.excludeExactNamePredicate
import analysis.granularity.method.filter.predicates.excludeWithAnnotations
import analysis.granularity.method.filter.predicates.excludeWithNamePrefixPredicate
import analysis.granularity.method.filter.predicates.minBodyLinesLengthPredicate
import com.fasterxml.jackson.databind.JsonNode
import java.util.function.Predicate

class MethodSummaryFilterConfig {
    private companion object {
        const val MIN_BODY_LINES_LEN = "min_body_lines_length"
        const val EXCL_EXACT_NAME = "exclude_with_exact_name"
        const val EXCL_NAME_PREF = "exclude_with_name_prefix"
        const val JAVA_EXCL_ANNOS = "JAVA_exclude_with_annotations"

        val INT_FIELDS = listOf(MIN_BODY_LINES_LEN)
        val STRING_FIELDS = listOf(EXCL_EXACT_NAME, EXCL_NAME_PREF, JAVA_EXCL_ANNOS)

        fun parseFromJson(jsonNode: JsonNode): MethodSummaryFilterConfig {
            val filterConfig = MethodSummaryFilterConfig()
            filterConfig.apply {
                jsonNode.parseFields()
            }
            return filterConfig
        }
    }

    // common
    var minBodyLinesLength = 0
    var excludeWithNamePrefix = mutableListOf<String>()
    var excludeWithExactName = mutableListOf<String>()

    // Java
    var javaExcludeWithAnnotation = mutableListOf<String>()

    fun getCommonPredicates(): List<Predicate<MethodSummary>> {
        val ps = mutableListOf<Predicate<MethodSummary>>()
        // method body length
        if (minBodyLinesLength != 0) {
            ps.add(minBodyLinesLengthPredicate(minBodyLinesLength))
        }
        // method exact name
        if (excludeWithExactName.isNotEmpty()) {
            ps.add(excludeExactNamePredicate(excludeWithExactName))
        }
        // method name prefix
        if (excludeWithNamePrefix.isNotEmpty()) {
            ps.add(excludeWithNamePrefixPredicate(excludeWithNamePrefix))
        }
        return ps
    }

    fun getJavaPredicates(): List<Predicate<MethodSummary>> {
        val ps = mutableListOf<Predicate<MethodSummary>>()
        // method annotation
        if (javaExcludeWithAnnotation.isNotEmpty()) {
            ps.add(excludeWithAnnotations(javaExcludeWithAnnotation))
        }
        return ps
    }

    fun JsonNode.parseFields() {
        this.parseIntFields()
        this.parseStringFields()
    }

    private fun JsonNode.parseIntFields() = INT_FIELDS.forEach { field ->
        val value = this.get(field)?.asInt() ?: return@forEach
        when (field) {
            MIN_BODY_LINES_LEN -> minBodyLinesLength = value
        }
    }

    private fun JsonNode.parseStringFields() = STRING_FIELDS.forEach { field ->
        val values = this.get(field)?.map { it.asText() }?.toList() ?: return@forEach
        when (field) {
            EXCL_NAME_PREF -> excludeWithNamePrefix.addAll(values)
            EXCL_EXACT_NAME -> excludeWithExactName.addAll(values)
            JAVA_EXCL_ANNOS -> javaExcludeWithAnnotation.addAll(values)
        }
    }
}
