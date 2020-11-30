import reposanalyzer.config.AnalysisConfig
import reposanalyzer.logic.RepoInfo
import reposanalyzer.logic.ReposAnalyzer
import reposanalyzer.logic.loadReposPatches

fun main() {
    println("input path to .json analysis config file: ")
    val configPath = readLine() ?: return
    println("input path to .json list of repositories URLs")
    val reposListPath = readLine() ?: return

    val analysisConfig = AnalysisConfig(configPath = configPath, isDebug = true)
    val reposAnalyzer = ReposAnalyzer(config = analysisConfig)
    reposAnalyzer.submitAll(
        loadReposPatches(reposListPath).map { RepoInfo(it) }
    )
}
