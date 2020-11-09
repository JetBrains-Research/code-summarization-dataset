import reposanalyzer.config.Granularity
import reposanalyzer.config.Language
import reposanalyzer.config.Task
import reposanalyzer.logic.ReposAnalyzer
import reposanalyzer.utils.loadReposPatches

const val PATH_TO_ALL_REPOS_LIST = "repos/repos.json"
const val DUMP_FOLDER = "repos/dumps"

fun main() {
    try {
        val reposPatches = loadReposPatches(PATH_TO_ALL_REPOS_LIST)
        val reposAnalyzer = ReposAnalyzer(
            reposPatches,
            DUMP_FOLDER,
            listOf(Language.JAVA),
            Task.NAME,
            Granularity.METHOD,
            silent = false
        )
        reposAnalyzer.init()
        reposAnalyzer.analyze()
    } catch (e: Exception) {
        println(e.message)
    }
}
