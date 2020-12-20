package reposfinder.filtering

import reposfinder.filtering.utils.isDateGood
import reposfinder.filtering.utils.isDateInRange
import reposfinder.filtering.utils.isIntValueGood
import reposfinder.logic.Repository
import java.time.LocalDate

enum class FilterType {
    CORE,
    GRAPHQL
}

interface Filter {
    var type: FilterType
    val field: Field

    fun isGood(repo: Repository): Boolean
}

class IntValueFilter(
    override val field: Field,
    val relation: Relation,
    val value: Long
) : Filter {

    override var type = FilterType.CORE

    override fun isGood(repo: Repository): Boolean {
        val repoValue = repo.info.get(field.gitHubName)?.asLong() ?: return false
        val result = relation.isIntValueGood(repoValue, value)
        repo.filterResults.add(
            FilterResult(
                field = field,
                repoValue = repoValue.toString(),
                filterValueMin = value.toString(),
                relation = relation,
                result = result
            )
        )
        return result
    }
}

class DateValueFilter(
    override val field: Field,
    val relation: Relation,
    val date: LocalDate
) : Filter {

    override var type = FilterType.CORE

    override fun isGood(repo: Repository): Boolean {
        val dateStr = repo.info.get(field.gitHubName)?.asText() ?: return false
        val repoDate = LocalDate.parse(dateStr.split('T')[0])
        val result = relation.isDateGood(repoDate, date)
        repo.filterResults.add(
            FilterResult(
                field = field,
                repoValue = repoDate.toString(),
                filterValueMin = date.toString(),
                relation = relation,
                result = result
            )
        )
        return result
    }
}

class StringValueFilter(
    override val field: Field,
    val values: List<String>,
    val relation: Relation = Relation.EQ
) : Filter {

    override var type = FilterType.CORE

    override fun isGood(repo: Repository): Boolean {
        val repoValue = repo.info.get(field.gitHubName)?.asText() ?: return false
        val result = values.any { it.equals(repoValue, ignoreCase = true) }
        repo.filterResults.add(
            FilterResult(
                field = field,
                repoValue = listOf(repoValue).toString(),
                filterValueMin = values.toString(),
                relation = relation,
                result = result
            )
        )
        return result
    }
}

class LicenseFilter(
    override val field: Field,
    val values: List<String>,
    var isLicense: Boolean = false,
    val relation: Relation = Relation.EQ
) : Filter {

    private companion object {
        const val KEY = "key"
        const val LICENSE = "license"
        const val NO_LICENSE = "no_license"
        const val ANY_LICENSE = "any_license"
    }

    override var type = FilterType.CORE

    override fun isGood(repo: Repository): Boolean {
        val repoValue = repo.info.get(field.gitHubName)?.get(KEY)?.asText()
        var result: Boolean
        var filterValueMin: String
        if (isLicense) {
            if (repoValue == null) {
                result = false
                filterValueMin = if (values.isEmpty()) "[$ANY_LICENSE]" else values.toString()
            } else if (values.isEmpty()) {
                result = true
                filterValueMin = "[$ANY_LICENSE]"
            } else {
                result = values.any { it.equals(repoValue, ignoreCase = true) }
                filterValueMin = values.toString()
            }
        } else {
            result = repoValue == null
            filterValueMin = "[$NO_LICENSE]"
        }
        repo.filterResults.add(
            FilterResult(
                field = field,
                repoValue = "[$repoValue]",
                filterValueMin = filterValueMin,
                relation = relation,
                result = result
            )
        )
        return result
    }
}

class BoolValueFilter(
    override val field: Field,
    val value: Boolean
) : Filter {

    override var type: FilterType = FilterType.CORE

    override fun isGood(repo: Repository): Boolean {
        val repoValue = repo.info.get(field.gitHubName)?.asBoolean() ?: return false
        val result = value == repoValue
        repo.filterResults.add(
            FilterResult(
                field = field,
                repoValue = repoValue.toString(),
                filterValueMin = value.toString(),
                relation = Relation.EQ,
                result = result
            )
        )
        return result
    }
}

class IntRangeFilter(
    override val field: Field,
    val range: LongRange
) : Filter {

    override var type = FilterType.CORE

    override fun isGood(repo: Repository): Boolean {
        val repoValue = repo.info.get(field.gitHubName)?.asLong() ?: return false
        val result = repoValue in range
        repo.filterResults.add(
            FilterResult(
                field = field,
                repoValue = repoValue.toString(),
                filterValueMin = range.first.toString(),
                filterValueMax = range.last.toString(),
                result = result
            )
        )
        return result
    }
}

class DateRangeFilter(
    override val field: Field,
    val dateRange: Pair<LocalDate, LocalDate>
) : Filter {

    override var type = FilterType.CORE

    override fun isGood(repo: Repository): Boolean {
        val dateStr = repo.info.get(field.gitHubName)?.asText() ?: return false
        val repoDate = LocalDate.parse(dateStr.split('T')[0])
        val result = repoDate.isDateInRange(dateRange)
        repo.filterResults.add(
            FilterResult(
                field = field,
                repoValue = repoDate.toString(),
                filterValueMin = dateRange.first.toString(),
                filterValueMax = dateRange.second.toString(),
                result = result
            )
        )
        return result
    }
}
