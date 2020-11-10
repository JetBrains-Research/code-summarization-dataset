package reposanalyzer.config

data class Config(
    val reposPatches: List<String>,
    val dumpFolder: String,
    val languages: List<Language>,
    val silent: Boolean = true, // TODO
    val hideMethodsNames: Boolean = true,
    val task: Task = Task.NAME,
    val granularity: Granularity = Granularity.METHOD,
    val excludeNodes: List<String> = listOf(),
    val excludeConstructors: Boolean = true,
    val copyDetection: Boolean = false,
    val summaryDumpThreshold: Int = 500,
    val logDumpThreshold: Int = 1000
)
