import reposprovider.utils.ProviderParser

fun main(args: Array<String>) = ProviderParser().main(args)

// import reposanalyzer.config.AnalysisConfig
// import reposanalyzer.logic.AnalysisRepository
// import reposanalyzer.logic.ReposAnalyzer
// import reposanalyzer.logic.loadJSONList

// fun main(args: Array<String>) {
//    val analysisConfig = AnalysisConfig(
//        configPath = "repos/analysis_config.json",
//        isDebugAnalyzer = true,
//        isDebugSummarizers = true
//    )
//    val reposAnalyzer = ReposAnalyzer(config = analysisConfig)
//    reposAnalyzer.submitAll(
//        loadJSONList(analysisConfig.reposUrlsPath).map {
//            val splitted = it.split(",")
//            val owner = splitted[0]
//            val name = splitted[1]
//            val license = splitted[2]
//            AnalysisRepository(owner = owner, name = name, licence = license)
//        }
//    )
//    reposAnalyzer.waitUntilAnyRunning()
// }
//
