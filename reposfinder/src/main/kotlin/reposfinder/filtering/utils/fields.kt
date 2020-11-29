package reposfinder.filtering.utils

import com.fasterxml.jackson.databind.JsonNode
import reposfinder.filtering.BoolValueFilter
import reposfinder.filtering.DateRangeFilter
import reposfinder.filtering.DateValueFilter
import reposfinder.filtering.Field
import reposfinder.filtering.Filter
import reposfinder.filtering.FilterType
import reposfinder.filtering.IntRangeFilter
import reposfinder.filtering.IntValueFilter
import reposfinder.filtering.Relation
import reposfinder.filtering.StringValueFilter
import java.time.LocalDate

fun Field.parseFilter(jsonNode: JsonNode): Filter? {
    val values = mutableListOf<String>()
    for (value in jsonNode.get(this.configName)) {
        values.add(value.asText())
    }
    if (values.isEmpty()) {
        return null
    }
    val relation = values[0].isRelation()
    return when (this) {
        Field.LANGUAGES -> StringValueFilter(this, values)
        Field.ANON_CONTRIBUTORS -> BoolValueFilter(this, values[0].toBoolean())
        Field.CREATED_AT, Field.PUSHED_AT, Field.UPDATED_AT -> this.getDateFilter(relation, values)
        Field.COMMITS -> {
            val filter = this.getIntFilter(relation, values)
            filter?.let {
                it.type = FilterType.GRAPHQL
            }
            filter
        }
        else -> this.getIntFilter(relation, values)
    }
}

fun Field.getIntFilter(relation: Relation?, values: List<String>): Filter? {
    return if (relation != null) {
        IntValueFilter(this, relation, values[1].toLong())
    } else when (values.size) {
        1 -> IntValueFilter(this, Relation.EQ, values[0].toLong())
        2 -> IntRangeFilter(this, values[0].toLong()..values[1].toLong())
        else -> null
    }
}

fun Field.getDateFilter(relation: Relation?, values: List<String>): Filter? {
    return if (relation != null) {
        DateValueFilter(this, relation, LocalDate.parse(values[1]))
    } else when (values.size) {
        1 -> DateValueFilter(this, Relation.EQ, LocalDate.parse(values[0]))
        2 -> DateRangeFilter(this, Pair(LocalDate.parse(values[0]), LocalDate.parse(values[1])))
        else -> null
    }
}
