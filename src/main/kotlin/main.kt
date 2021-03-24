import reposanalyzer.config.AnalysisConfig
import reposanalyzer.config.loadJSONList
import reposanalyzer.config.loadPaths
import reposanalyzer.config.parseRepoUrls
import reposanalyzer.logic.AnalysisRepository
import reposanalyzer.logic.ReposAnalyzer
import java.io.File

// fun main(args: Array<String>) = ProviderParser().main(args)

fun main() {
    val analysisConfig = AnalysisConfig(
        configPath = "repos/analysis_config.json",
        isDebugAnalyzer = true,
        isDebugSummarizers = true
    )
    val reposAnalyzer = ReposAnalyzer(config = analysisConfig)

//    reposAnalyzer.submitAllRepos(
//        loadJSONList(analysisConfig.reposUrlsPath).parseRepoUrls().subList(0, 10).map { p ->
//            AnalysisRepository(owner = p.first, name = p.second)
//        }
//    )
//
//    reposAnalyzer.submitRepo(
//        AnalysisRepository(path = "path/to/loaded/repository")
//    )
//
//    reposAnalyzer.submitRepo(
//        AnalysisRepository(owner = "JetBrains-Research", name = "pythonparser")
//    )
//
    reposAnalyzer.submitDir(File("XML"))
//    reposAnalyzer.submitDir(File("/home/vancho/Master_UBUNTU/12_Java_I/hw/hw_2_trie"))

    reposAnalyzer.waitUntilAnyRunning()
}
