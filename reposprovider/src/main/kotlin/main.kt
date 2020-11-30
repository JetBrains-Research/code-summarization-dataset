import reposanalyzer.config.AnalysisConfig
import reposfinder.config.SearchConfig
import reposprovider.logic.SearchAnalysisProvider

fun main() {
    println("input path to .json analysis config file: ")
    val analysisConfigPath: String = readLine() ?: return
    println("input path to .json search config file: ")
    val searchConfigPath: String = readLine() ?: return

    val analysisConfig = AnalysisConfig(configPath = analysisConfigPath, isDebug = true)
    val searchConfig = SearchConfig(configPath = searchConfigPath, isDebug = true)
    val provider = SearchAnalysisProvider(searchConfig = searchConfig, analysisConfig = analysisConfig)
    provider.run()
}
