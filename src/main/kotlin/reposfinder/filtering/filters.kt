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
    private val relation: Relation,
    private val value: Long
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
    private val relation: Relation,
    private val date: LocalDate
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
    private val values: List<String>,
    private val relation: Relation = Relation.EQ
) : Filter {

    override var type = FilterType.CORE

    override fun isGood(repo: Repository): Boolean {
        val repoValue = repo.info.get(field.gitHubName)?.asText() ?: return false
        val result = values.any { it.toLowerCase() == repoValue.toLowerCase() }
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

class BoolValueFilter(
    override val field: Field,
    private val value: Boolean
) : Filter {

    override var type: FilterType = FilterType.CORE

    override fun isGood(repo: Repository): Boolean = value
}

class IntRangeFilter(
    override val field: Field,
    private val range: LongRange
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
    private val dateRange: Pair<LocalDate, LocalDate>
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
