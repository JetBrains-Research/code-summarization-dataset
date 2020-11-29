package reposanalyzer.config

import astminer.cli.ConstructorFilterPredicate
import astminer.cli.MethodFilterPredicate

class Config(
    val dumpFolder: String,
    val languages: List<Language>,
    val task: Task = Task.NAME,
    val hideMethodName: Boolean = true,
    val commitsType: CommitsType = CommitsType.FIRST_PARENTS_INCLUDE_MERGES,
    val excludeNodes: List<String> = listOf(),
    val granularity: Granularity = Granularity.METHOD,
    val copyDetection: Boolean = false,
    val excludeConstructors: Boolean = true,
    val logDumpThreshold: Int = 200,
    val summaryDumpThreshold: Int = 200,
    val isDebug: Boolean = true
) {
    enum class CommitsType {
        ONLY_MERGES,
        FIRST_PARENTS_INCLUDE_MERGES
    }

    val filterPredicates = mutableListOf<MethodFilterPredicate>()
    val supportedExtensions = languages.flatMap { it.extensions }

    init {
        if (excludeConstructors) {
            filterPredicates.add(ConstructorFilterPredicate())
        }
    }
}
