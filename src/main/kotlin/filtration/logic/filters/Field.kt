package filtration.logic.filters

/*
 *  Conversion between config field name and GitHub API field name
 */
enum class Field(val configName: String, val gitHubName: String) {
    LANGUAGES("languages", "language"),
    STARS("stars_count", "stargazers_count"),
    FORK("is_fork", "fork"),
    IS_LICENSE("is_license", "license"),
    LICENSES("licenses", "license"),
    COMMITS("commits_count", "commits_count"),
    CONTRIBUTORS("contributors_count", "contributors_count"),
    ANON_CONTRIBUTORS("anon_contributors", ""),
    FORKS("forks_count", "forks_count"),
    WATCHERS("watchers_count", "watchers_count"),
    SIZE("size_KB", "size"),
    OPEN_ISSUES("open_issues_count", "open_issues_count"),
    SUBSCRIBERS("subscribers_count", "subscribers_count"),
    CREATED_AT("created_at", "created_at"),
    UPDATED_AT("updated_at", "updated_at"),
    PUSHED_AT("pushed_at", "pushed_at")
}
