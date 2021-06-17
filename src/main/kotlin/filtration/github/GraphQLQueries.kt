package filtration.github

import com.fasterxml.jackson.databind.ObjectMapper

enum class GraphQLQueries(val target: String) {
    COMMITS_COUNT(
        target = """target { ... on Commit { history (first: 1) { totalCount pageInfo { endCursor } } } }"""
    );

    fun query(owner: String, repoName: String, target: String): String =
        """{ repository(owner: "$owner", name: "$repoName") { defaultBranchRef { $target } } }"""

    fun getGraphQLBody(query: String, objectMapper: ObjectMapper): String =
        objectMapper.writeValueAsString(mapOf("query" to query))
}
