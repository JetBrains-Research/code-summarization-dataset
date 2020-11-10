package reposanalyzer.logic

import astminer.cli.ConstructorFilterPredicate
import astminer.cli.MethodFilterPredicate
import reposanalyzer.config.Config
import reposanalyzer.utils.dotGitFilter
import java.io.File

class ReposAnalyzer(config: Config) {
    private val workers = mutableListOf<RepoSummarizer>()
    private var goodPatches: List<String>
    private var badPatches: List<String>

    init {
        File(config.dumpFolder).mkdirs()
        val (good, bad) = dotGitFilter(config.reposPatches)
        goodPatches = good
        badPatches = bad
        goodPatches.forEach { repoPath ->
            val repoDumpFolder = config.dumpFolder + File.separator + repoPath.substringAfterLast(File.separator)
            val filterPredicates = mutableListOf<MethodFilterPredicate>()
            if (config.excludeConstructors) {
                filterPredicates.add(ConstructorFilterPredicate())
            }
            val summarizer = RepoSummarizer(repoPath, repoDumpFolder, config, filterPredicates)
            workers.add(summarizer)
        }
    }

    fun init() {
        workers.forEach {
            it.init()
        }
    }

    fun analyze() {
        workers.forEach {
            it.run()
        }
    }

    fun addRepo(path: String) {
        TODO()
    }
}
