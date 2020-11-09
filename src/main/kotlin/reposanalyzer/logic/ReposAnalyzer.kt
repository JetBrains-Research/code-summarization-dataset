package reposanalyzer.logic

import reposanalyzer.config.Granularity
import reposanalyzer.config.Language
import reposanalyzer.config.Task
import reposanalyzer.utils.dotGitFilter
import java.io.File

class ReposAnalyzer(
    reposPatches: List<String>,
    dumpFolder: String,
    languages: List<Language>,
    task: Task = Task.NAME,
    granularity: Granularity = Granularity.METHOD,
    hideMethodsNames: Boolean = true,
    silent: Boolean = false
) {
    private val workers = mutableListOf<RepoSummarizer>()
    private var goodPatches: List<String>
    private var badPatches: List<String>

    init {
        File(dumpFolder).mkdirs()
        val (good, bad) = dotGitFilter(reposPatches)
        goodPatches = good
        badPatches = bad
        goodPatches.forEach { path ->
            val dumpPath = dumpFolder + File.separator + path.substringAfterLast(File.separator)
            val summarizer = RepoSummarizer(
                path,
                dumpPath,
                languages,
                task,
                granularity,
                hideMethodsNames,
                silent
            )
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
}
