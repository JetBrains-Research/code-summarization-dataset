import reposfinder.config.SearchConfig
import reposfinder.logic.ReposFinder

fun main() {
    println("input path to .json config file: ")
    val configPath = readLine() ?: return

    val searchConfig = SearchConfig(configPath = configPath, isDebug = true)
    val reposFinder = ReposFinder(config = searchConfig)
    reposFinder.run()
}
