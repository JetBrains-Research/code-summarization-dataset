package reposfinder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.Response

import reposfinder.api.GitHubAPI
import reposfinder.api.GraphQLQueries
import reposfinder.config.Config
import reposfinder.logic.Relation
import reposfinder.logic.isDateGood
import reposfinder.logic.isDateInRange
import reposfinder.logic.isIntValueGood
import reposfinder.logic.isIntValueInRange
import reposfinder.requests.getRequest
import reposfinder.requests.postRequest

import java.io.File
import java.time.LocalDate
import java.time.LocalTime

import kotlin.math.min

/*
 * GitHub API v3 search only first 1000 results
 * https://docs.github.com/en/free-pro-team@latest/rest/reference/search#ranking-search-results
 */
class ReposFinder(private val dumpDirectory: String, private val token: String? = null) {
    private companion object {
        private const val ITEMS = "items"
        private const val TOTAL_CNT = "total_count"
        private const val HTML_URL = "html_url"
        private const val CONTRIBUTORS_URL = "contributors_url"
        private const val PER_PAGE = 100
        private const val SLEEP_20 = 20L
        private const val SLEEP_200 = 200L
        private const val BAD_REQUEST = "ReposFinder BAD REQUEST"
    }

    private val repositories = mutableListOf<JsonNode>()
    private val contributorsCount = mutableMapOf<String, Int>()
    private val commitsCount = mutableMapOf<String, Int>()
    private val endCursors = mutableMapOf<String, String>()
    private val firstCommitDates = mutableMapOf<String, LocalDate>()

    private val outputStream = System.out

    private val objectMapper = jacksonObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)

    fun search(): Boolean {
        val url = GitHubAPI.RepositoriesSearch.url + Config.settings.v3query + perPage(PER_PAGE)
        outMessage("API v3 query: ${Config.settings.v3query}")

        // 1. first page with first repos list and total pages count
        val (request, response, result) = getRequest(url, token)
        val body = getResponseBody(response)

        // bad request
        if (isBadRequest(response)) {
            errorMessage(BAD_REQUEST, "API v3 first page", body)
            return false
        }

        val node = objectMapper.readTree(body)
        val reposCount: Int = node.get(TOTAL_CNT).asInt()
        val pagesCount: Int = (reposCount + PER_PAGE) / PER_PAGE

        outMessage("API v3 found: $reposCount repositories")
        outMessage("API v3 pages: $pagesCount with $PER_PAGE repos per page")
        outMessage("Traversing only first ${min(pagesCount, 10)} pages with ${min(reposCount, 1000)} repositories")

        val list: List<JsonNode> = getJsonNodesList(node.get(ITEMS))
        repositories.addAll(list)

        // 2. pages 2..N -- API v3 main search
        if (!traversePages(url, 2..min(pagesCount, 10))) {
            errorMessage("API v3 SEARCH ended with ERRORS")
            return false
        }

        // 3. contributors search -- API v3 pagination hack
        if (isContributorsFilter()) {
            outMessage("API v3 before contributors filter: ${repositories.size} repositories")

            if (!contributorsSearch()) {
                return false
            }
            // filtering
            filterByInt(
                Config.settings.repoSearch.contributorsValue,
                Config.settings.repoSearch.contributorsRange,
                contributorsCount
            )
            outMessage("API v3 after contributors filter: ${repositories.size} repositories")
        }

        // 4. commits count search -- GraphQL API
        if (isCommitsCountFilter()) {
            outMessage("GraphQL API before commits filter: ${repositories.size} repositories")
            if (!commitsInfoSearch()) {
                return false
            }
            // filtering
            filterByInt(
                Config.settings.repoSearch.commitsValue,
                Config.settings.repoSearch.commitsRange,
                commitsCount
            )
            outMessage("GraphQL API after commits filter: ${repositories.size} repositories")
        }

        // 5. first commit date search -- GraphQL API
        if (isFirstCommitFilter()) {
            outMessage("GraphQL API before first commit date filter: ${repositories.size} repositories")
            if (!isCommitsCountFilter()) {
                if (!commitsInfoSearch()) {
                    return false
                }
            }
            if (!firstCommitDateSearch()) {
                return false
            }
            // filtering
            filterByDate(
                Config.settings.repoSearch.firstCommitValue,
                Config.settings.repoSearch.firstCommitRange,
                firstCommitDates
            )
            outMessage("GraphQL API after first commit date filter: ${repositories.size} repositories")
        }
        return true
    }

    private fun handleOwnerJSON(node: JsonNode): ObjectNode {
        val ownerNode = objectMapper.createObjectNode()
        ownerNode.set<JsonNode>(HTML_URL, node.get(HTML_URL))
        for (field in node.fieldNames()) {
            if (field.endsWith("_url")) { // api links
                continue
            }
            ownerNode.set<JsonNode>(field, node.get(field))
        }
        return ownerNode
    }

    private fun handleRepoJSON(node: JsonNode): ObjectNode {
        val repoNode = objectMapper.createObjectNode()
        val repoURL = node.get(HTML_URL).asText()
        if (commitsCount.containsKey(repoURL)) {
            repoNode.put("commits_cnt", commitsCount[repoURL])
        }
        if (firstCommitDates.containsKey(repoURL)) {
            repoNode.put("fst_commit_date", firstCommitDates[repoURL].toString())
        }
        for (field in node.fieldNames()) {
            if (field.endsWith("_url") && field != HTML_URL) { // api links
                continue
            }
            if (field == "owner") {
                val ownerNode = handleOwnerJSON(node.get(field))
                repoNode.set<JsonNode>("owner", ownerNode)
            } else {
                repoNode.set<JsonNode>(field, node.get(field))
            }
        }
        return repoNode
    }

    fun dumpResults() {
        if (!File(dumpDirectory).exists()) {
            File(dumpDirectory).mkdirs()
        }
        val reposJSON = mutableListOf<JsonNode>()
        for (repo in repositories) {
            val repoNode = handleRepoJSON(repo)
            reposJSON.add(repoNode)
        }
        val localTime = LocalTime.now()
        val file = File(
            "$dumpDirectory${File.separator}repos_${LocalDate.now()}" +
                "_${localTime.hour}_${localTime.minute}.json"
        )
        objectMapper.writeValue(file, reposJSON)

        if (file.exists()) {
            outMessage("> data was dumped in ${file.path}")
        }
    }

    private fun traversePages(url: String, pages: IntRange): Boolean {
        outMessage("Traversed pages: 1", false)
        for (page in pages) {
            val newURL = url + nthPage(page)
            val (request, response, result) = getRequest(newURL, token)
            val body = response.body().asString(response.headers[Headers.CONTENT_TYPE].lastOrNull())

            if (isBadRequest(response)) {
                errorMessage(BAD_REQUEST, "on $page", body)
                return false
            }

            val node = objectMapper.readTree(body)
            node?.let {
                val list: List<JsonNode> = getJsonNodesList(node.get(ITEMS))
                repositories.addAll(list)
                Thread.sleep(SLEEP_200) // !!! thread sleep
            }
            if (node == null) {
                return false
            }
            outMessage(" $page", false)
        }
        outMessage("")
        return true
    }

    private fun filterByInt(goodValue: Pair<Relation, Int>?, goodRange: IntRange?, map: Map<String, Int>) {
        val goodRepos = mutableListOf<JsonNode>()
        for (repo in repositories) {
            val repoURL: String = repo.get(HTML_URL).asText()
            if (goodValue != null) {
                map[repoURL]?.let {
                    if (isIntValueGood(goodValue.first, goodValue.second, it)) {
                        goodRepos.add(repo)
                    }
                }
            } else if (goodRange != null) {
                map[repoURL]?.let {
                    if (isIntValueInRange(goodRange, it)) {
                        goodRepos.add(repo)
                    }
                }
            }
        }
        repositories.clear()
        repositories.addAll(goodRepos)
    }

    private fun filterByDate(
        goodValue: Pair<Relation, LocalDate>?,
        goodRange: Pair<LocalDate, LocalDate>?,
        map: Map<String, LocalDate>
    ) {
        val goodRepos = mutableListOf<JsonNode>()
        for (repo in repositories) {
            val repoURL: String = repo.get(HTML_URL).asText()
            if (goodValue != null) {
                map[repoURL]?.let {
                    if (isDateGood(goodValue.first, goodValue.second, it)) {
                        goodRepos.add(repo)
                    }
                }
            } else if (goodRange != null) {
                map[repoURL]?.let {
                    if (isDateInRange(goodRange, it)) {
                        goodRepos.add(repo)
                    }
                }
            }
        }
        repositories.clear()
        repositories.addAll(goodRepos)
    }

    private fun contributorsSearch(): Boolean {
        outMessage("API v3 contributors count search... ", false)
        for ((idx, repo) in repositories.withIndex()) {
            progressBar(idx, repositories.size)

            var contributorsURL = repo.get(CONTRIBUTORS_URL).asText()
            contributorsURL += "?${perPage(1)}"

            // anon contributors
            if (Config.anonContributors) {
                contributorsURL += anonContribs()
            }

            val (request, response, result) = getRequest(contributorsURL, token)
            val body = getResponseBody(response)
            if (isBadRequest(response)) {
                errorMessage(BAD_REQUEST, "CONTRIBUTORS SEARCH url: $contributorsURL", body)
                return false
            }

            val count = getContributorsCount(response)
            val repositoryURL = repo.get(HTML_URL).asText()
            contributorsCount[repositoryURL] = count
            Thread.sleep(SLEEP_20) // !!! thread sleep
        }
        outMessage("")
        return true
    }

    private fun commitsInfoSearch(): Boolean {
        outMessage("GraphQL API commits info search... ", false)
        for ((idx, repo) in repositories.withIndex()) {
            progressBar(idx, repositories.size)

            val name = repo.get("name").asText()
            val owner = repo.get("owner").get("login").asText()
            val repoURL: String = repo.get(HTML_URL).asText()
            val totalCountBody = getGraphQLBody(GraphQLQueries.totalCountEndCursor(owner, name))

            val (request, response, result) = postRequest(GitHubAPI.GraphQL.url, token, totalCountBody)
            val body = getResponseBody(response)
            if (isBadRequest(response)) {
                errorMessage(BAD_REQUEST, "COMMITS SEARCH owner: $owner, repo: $name", body)
                return false
            }

            val node = objectMapper.readTree(body)
            val history = node.get("data")
                .get("repository")
                .get("defaultBranchRef")
                .get("target")
                .get("history")
            val totalCount = history.get("totalCount").asInt()
            val endCursor = history.get("pageInfo")
                .get("endCursor")
                .asText()
                .split(" ")[0]

            commitsCount[repoURL] = totalCount
            endCursors[repoURL] = endCursor
            Thread.sleep(SLEEP_20) // !!! thread sleep
        }
        outMessage("")
        return true
    }

    private fun firstCommitDateSearch(): Boolean {
        outMessage("GraphQL API first commit date search... ", false)
        for ((idx, repo) in repositories.withIndex()) {
            progressBar(idx, repositories.size)
            val repoURL: String = repo.get(HTML_URL).asText()
            if (!endCursors.containsKey(repoURL) || !commitsCount.containsKey(repoURL)) {
                continue
            }
            val name = repo.get("name").asText()
            val owner = repo.get("owner").get("login").asText()
            var totalCount = commitsCount[repoURL]
            if (totalCount!! < 3) {
                continue
            }
            totalCount -= 2
            val endCursor = endCursors[repoURL]
            val dateBody = getGraphQLBody(GraphQLQueries.firstCommitInfo(owner, name, endCursor!!, totalCount))

            val (request, response, result) = postRequest(GitHubAPI.GraphQL.url, token, dateBody)
            val body = getResponseBody(response)
            if (isBadRequest(response)) {
                errorMessage(BAD_REQUEST, "FIRST COMMIT DATE owner: $owner, repo: $name", body)
                return false
            }
            val node = objectMapper.readTree(body)
            val commitsNode = node.get("data")
                .get("repository")
                .get("defaultBranchRef")
                .get("target")
                .get("history")
                .get("nodes")
            val commitsList = getJsonNodesList(commitsNode)
            if (commitsList.isNotEmpty()) {
                val date = commitsList[0].get("committedDate").asText().split("T")[0]
                firstCommitDates[repoURL] = LocalDate.parse(date)
            }
            Thread.sleep(SLEEP_20) // !!! thread sleep
        }
        outMessage("")
        return true
    }

    private fun isBadRequest(response: Response): Boolean {
        return response.statusCode != 200
    }

    private fun isContributorsFilter(): Boolean {
        return Config.settings.repoSearch.contributorsRange != null ||
            Config.settings.repoSearch.contributorsValue != null
    }

    private fun isCommitsCountFilter(): Boolean {
        return Config.settings.repoSearch.commitsRange != null ||
            Config.settings.repoSearch.commitsValue != null
    }

    private fun isFirstCommitFilter(): Boolean {
        return Config.settings.repoSearch.firstCommitRange != null ||
            Config.settings.repoSearch.firstCommitValue != null
    }

    private fun getGraphQLBody(queryBody: String): String {
        return objectMapper.writeValueAsString(mapOf("query" to queryBody))
    }

    private fun getResponseBody(response: Response): String {
        return response.body().asString(response.headers[Headers.CONTENT_TYPE].lastOrNull())
    }

    private fun getJsonNodesList(node: JsonNode): List<JsonNode> {
        return objectMapper.readValue(node.toPrettyString())
    }

    // contributors count with pagination hack
    private fun getContributorsCount(response: Response): Int {
        // no next pages => only one contributor
        if (!response.headers.containsKey("Link")) {
            return 1
        }
        val link = response.headers["Link"].toList()[0].split(",")
            .filter {
                it.contains("rel=\"last\"")
            }.toList()[0]
        return link.substringAfter("&page=")
            .substringBefore(">")
            .toInt()
    }

    private fun progressBar(iteration: Int, size: Int) {
        if (iteration < 2 * size) {
            return
        }
        when ((iteration + 1) / size * 100) {
            25 -> outMessage("25%", false)
            35 -> outMessage("..35%", false)
            45 -> outMessage("..45%", false)
            55 -> outMessage("..55%", false)
            70 -> outMessage("..70%", false)
            80 -> outMessage("..80%", false)
            90 -> outMessage("..75%", false)
            100 -> outMessage("..100%", false)
        }
    }

    private fun errorMessage(type: String, arg: String? = null, body: String? = null) {
        when (arg) {
            null -> outputStream.println(type)
            else -> outputStream.println("$type: $arg")
        }
        body?.let {
            outputStream.println(body)
        }
    }

    private fun outMessage(msg: String, newLine: Boolean = true) {
        when (newLine) {
            true -> outputStream.println(msg)
            false -> outputStream.print(msg)
        }
    }

    private fun perPage(n: Int): String {
        return "&per_page=$n"
    }

    private fun nthPage(n: Int): String {
        return "&page=$n"
    }

    private fun anonContribs(): String {
        return "&anon=true"
    }
}
