package search.api

enum class GitHubAPI(val url: String) {
    REPOS("https://api.github.com/repos"),
    LIMITS("https://api.github.com/rate_limit"),
    GRAPHQL("https://api.github.com/graphql");

    private companion object {
        const val CONTRIBUTORS = "contributors"
        const val PER_PAGE = "per_page"
        const val ANON = "anon"
    }

    object URL {
        fun core(owner: String, repoName: String): String = REPOS.url + "/$owner/$repoName"

        fun contributors(owner: String, repoName: String, perPage: Int = 1, isAnon: Boolean = false): String =
            core(owner, repoName) + "/$CONTRIBUTORS?$PER_PAGE=$perPage&$ANON=$isAnon"
    }
}
