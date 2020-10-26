package reposfinder.api

enum class GitHubAPI(val url: String) {
    Main("https://api.github.com/"),
    GraphQL("https://api.github.com/graphql"),
    RepositoriesSearch("https://api.github.com/search/repositories?"),
    ReposOrgRepo("https://api.github.com/repos/")
}

enum class APIQ(val type: String) {
    FOLLOWERS("followers:"),
    CREATED("created:"),
    LANG("language:"),
    STARS("stars:"),
    FORKS("forks:"),
    PUSH("pushed:"),
    PUBLIC("is:"),
    SIZE("size:"),
    USER("user:"),
    ORG("org:")
}

object GraphQLQueries {
    private fun query(owner: String, repoName: String, target: String): String {
        return """{ repository(owner: "$owner", name: "$repoName") { defaultBranchRef { $target } } }""".trimMargin()
    }

    fun totalCountEndCursor(owner: String, repoName: String): String {
        return query(
            owner, repoName,
            """target { ... on Commit { history (first: 1) { totalCount pageInfo { endCursor } } } }"""
        )
    }

    fun firstCommitInfo(owner: String, repoName: String, hash: String, count: Int): String {
        return query(
            owner, repoName,
            """target { ... on Commit { history (first: 1, after: "$hash $count") { 
                          nodes { message committedDate authoredDate oid author { email name } } } } }"""
        )
    }
}
