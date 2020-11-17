package reposfinder.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import reposfinder.filtering.Field
import reposfinder.filtering.Filter
import reposfinder.filtering.FilterType
import reposfinder.filtering.utils.parseFilter
import reposfinder.logic.Repository
import java.io.File

class SearchConfig(
    private val configPath: String
) {
    private companion object {
        const val SIZE_1 = 1
    }
    // filters
    val coreFilters = mutableListOf<Filter>()
    val graphQLFilters = mutableListOf<Filter>()

    // flags
    var isCore = false
    var isGraphQL = false
    var isCommitsCount = false
    var isContributors = false
    var isAnonContributors = false
    var isOnlyContributors = false

    init {
        processConfigFile()
        updateFlags()
    }

    private fun processConfigFile() {
        val file = File(configPath)
        val jsonMapper = jacksonObjectMapper()
        val jsonNode = jsonMapper.readValue<JsonNode>(file)
        for (field in Field.values().filter { jsonNode.has(it.configName) }) {
            val filter = field.parseFilter(jsonNode) ?: continue
            if (filter.field == Field.ANON_CONTRIBUTORS) {
                isAnonContributors = filter.isGood(
                    Repository("", "", jsonMapper.createObjectNode())
                )
            } else when (filter.type) {
                FilterType.CORE -> coreFilters.add(filter)
                FilterType.GRAPHQL -> graphQLFilters.add(filter)
            }
        }
    }

    private fun updateFlags() {
        isCore = coreFilters.isNotEmpty()
        isGraphQL = graphQLFilters.isNotEmpty()
        coreFilters.forEach { filter ->
            if (filter.field == Field.CONTRIBUTORS) {
                isContributors = true
            }
        }
        graphQLFilters.forEach { filter ->
            if (filter.field == Field.COMMITS) {
                isCommitsCount = true
            }
        }
        if (coreFilters.size == SIZE_1 && isContributors) {
            isOnlyContributors = true
        }
    }
}
