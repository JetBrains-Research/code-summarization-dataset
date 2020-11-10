import reposanalyzer.config.Config
import reposanalyzer.config.Granularity
import reposanalyzer.config.Language
import reposanalyzer.config.Task
import reposanalyzer.logic.ReposAnalyzer
import reposanalyzer.logic.loadReposPatches

const val PATH_TO_ALL_REPOS_LIST = "repos/repos.json"
const val DUMP_FOLDER = "repos/dumps"

fun main() {
    val reposPatches = loadReposPatches(PATH_TO_ALL_REPOS_LIST)
    val config = Config(
        dumpFolder = DUMP_FOLDER,
        reposPatches = reposPatches,
        task = Task.NAME,
        granularity = Granularity.METHOD,
        languages = listOf(Language.JAVA),
        excludeConstructors = true,
        hideMethodsNames = true
    )
    val reposAnalyzer = ReposAnalyzer(config = config)
    reposAnalyzer.init()
    reposAnalyzer.analyze()
}
