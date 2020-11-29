import reposfinder.config.Config
import reposfinder.logic.ReposFinder

fun main() {
    println("Please, input path to .json config file: ")
    val configPath = readLine()
    if (configPath != null) {
        val finder = ReposFinder(Config(configPath, isDebug = true))
        finder.run()
    }
}
