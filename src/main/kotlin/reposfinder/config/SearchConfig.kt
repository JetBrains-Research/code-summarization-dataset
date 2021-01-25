package reposfinder.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import reposfinder.filtering.BoolValueFilter
import reposfinder.filtering.Field
import reposfinder.filtering.Filter
import reposfinder.filtering.FilterType
import reposfinder.filtering.LicenseFilter
import reposfinder.filtering.utils.parseFilter
import reposfinder.utils.SearchConfigException
import java.io.File

class SearchConfig(
    val configPath: String,
    val isDebug: Boolean = false
) {
    private companion object {
        const val MILLIS_IN_HOUR = 3600000
        const val DEFAULT_DUMP_THRESHOLD = 50
        const val DEFAULT_SLEEP_RANGE: Long = 5 * 60 * 1000 // N * 60 000 milliseconds == N * 60 seconds == N minutes
        const val DEFAULT_WAIT_TIME: Long = 200 // milliseconds

        const val LOG_PATH = "log.txt"
        const val TOKEN_PATH = "token_path"
        const val DUMP_DIR_PATH = "dump_dir_path"
        const val REPOS_URLS_PATH = "repos_urls_path"
        const val DUMP_THRESHOLD = "dump_threshold"
    }
    val sleepRange: Long = DEFAULT_SLEEP_RANGE

    // dirs
    lateinit var dumpDir: String
    lateinit var token: String
    lateinit var urls: List<String>
    val logPath: String

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
    var isLicense: Boolean? = null

    // dumps
    var sleepTimeBetweenRequests: Long = DEFAULT_WAIT_TIME
    var reposDumpThreshold: Int = DEFAULT_DUMP_THRESHOLD

    init {
        val file = File(configPath)
        val jsonMapper = jacksonObjectMapper()
        val jsonNode = jsonMapper.readValue<JsonNode>(file)
        jsonNode.processWorkPart()
        jsonNode.processSearchPart()
        updateFlags()
        File(dumpDir).mkdirs()
        logPath = dumpDir + File.separator + LOG_PATH
    }

    fun reposPerHour(): Long {
        var pausePerRepo = 0L
        if (isCore) {
            pausePerRepo += this.sleepTimeBetweenRequests
        }
        if (isGraphQL) {
            pausePerRepo += this.sleepTimeBetweenRequests
        }
        if (isContributors) {
            pausePerRepo += this.sleepTimeBetweenRequests
        }
        if (pausePerRepo == 0L) {
            pausePerRepo = 1L
        }
        return MILLIS_IN_HOUR / pausePerRepo
    }

    private fun JsonNode.processWorkPart() {
        this.checkFields()
        token = this.get(TOKEN_PATH).asText().readToken()
        urls = this.get(REPOS_URLS_PATH).asText().readUrls()
        dumpDir = this.get(DUMP_DIR_PATH).asText()
        reposDumpThreshold = this.get(DUMP_THRESHOLD).asInt()
    }

    /*
     *   1. is_license : []:
     *          - any value in license field in GitHub .json (null or not null)
     *          - ignore values in licenses field in search_config.json
     *
     *
     *   2. is_license : [false]:
     *          - null license field in GitHub .json
     *          - ignore values in licenses field in search_config.json
     *
     *
     *   3. is_license : [true]:
     *          - not null license field in GitHub .json
     *
     *     + licenses : []:
     *           - any license in GitHub .json
     *     + licenses : [not_empty]
     *           - only licenses from list
     */
    private fun JsonNode.processSearchPart() {
        var licenseFilter: Filter? = null
        for (field in Field.values().filter { this.has(it.configName) }) {
            val filter = field.parseFilter(this) ?: continue
            when (filter.field) {
                // anon contributors is api argument, not filter
                Field.ANON_CONTRIBUTORS -> isAnonContributors = (filter as BoolValueFilter).value
                Field.IS_LICENSE -> isLicense = (filter as BoolValueFilter).value
                Field.LICENSES -> licenseFilter = filter
                else -> when (filter.type) {
                    FilterType.CORE -> coreFilters.add(filter)
                    FilterType.GRAPHQL -> graphQLFilters.add(filter)
                }
            }
        }
        isLicense?.let { itLicense ->
            licenseFilter?.let { filter ->
                (filter as LicenseFilter).isLicense = itLicense
                coreFilters.add(filter)
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
        if (coreFilters.size == 1 && isContributors) {
            isOnlyContributors = true
        }
    }

    private fun JsonNode.checkFields() {
        if (!this.has(TOKEN_PATH)) {
            TOKEN_PATH
        } else if (!this.has(DUMP_DIR_PATH)) {
            DUMP_DIR_PATH
        } else if (!this.has(REPOS_URLS_PATH)) {
            REPOS_URLS_PATH
        } else if (!this.has(DUMP_THRESHOLD)) {
            DUMP_THRESHOLD
        } else {
            null
        }?.let {
            throw SearchConfigException("no $it field in config")
        }

        if (this.get(TOKEN_PATH).asText().isEmpty()) {
            TOKEN_PATH
        } else if (this.get(DUMP_DIR_PATH).asText().isEmpty()) {
            DUMP_DIR_PATH
        } else if (this.get(REPOS_URLS_PATH).asText().isEmpty()) {
            REPOS_URLS_PATH
        } else if (this.get(DUMP_THRESHOLD).asText().isEmpty()) {
            DUMP_THRESHOLD
        } else {
            null
        }?.let {
            throw SearchConfigException("config field $it is empty")
        }
    }
}
