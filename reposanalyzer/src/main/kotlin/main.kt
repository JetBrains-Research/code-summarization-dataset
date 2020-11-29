import reposanalyzer.config.SearchConfig
import reposanalyzer.config.Granularity
import reposanalyzer.config.Language
import reposanalyzer.config.Task
import reposanalyzer.logic.RepoInfo
import reposanalyzer.logic.ReposAnalyzer
import reposanalyzer.logic.loadReposPatches

const val PATH_TO_ALL_REPOS_LIST = "repos/repos.json"
const val DUMP_FOLDER = "repos/dumps"

fun main() {
    val config = SearchConfig(
        dumpFolder = DUMP_FOLDER,
        task = Task.NAME,
        commitsType = SearchConfig.CommitsType.FIRST_PARENTS_INCLUDE_MERGES,
        granularity = Granularity.METHOD,
        languages = listOf(Language.JAVA),
        excludeConstructors = true,
        hideMethodName = true
    )
    val reposPatches = loadReposPatches(PATH_TO_ALL_REPOS_LIST)
    val reposAnalyzer = ReposAnalyzer(config = config)
    reposAnalyzer.addAllRepos(reposPatches.map { RepoInfo(it) })
    reposAnalyzer.run()
}
