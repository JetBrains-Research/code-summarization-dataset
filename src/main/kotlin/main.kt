import reposfinder.config.SearchConfig
import reposfinder.config.WorkConfig
import reposfinder.logic.ReposFinder
import reposfinder.utils.readList
import reposfinder.utils.readToken

private const val DUMP_DIR = "repos/results"
private const val URLS_PATH = "repos/urls.json"
private const val CONFIG_PATH = "repos/config.json"
private const val TOKEN_PATH = "repos/token.txt"

fun main() {
    val finder = ReposFinder(
        URLS_PATH.readList(),
        WorkConfig(
            dumpDir = DUMP_DIR,
            token = TOKEN_PATH.readToken()
        ),
        SearchConfig(configPath = CONFIG_PATH)
    )
    finder.run()
}
