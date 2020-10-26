import reposfinder.ReposFinder
import reposfinder.utils.tokenReader

fun main() {
    try {
        val reposFinder = ReposFinder("search_results", tokenReader("path/to/token"))
        reposFinder.search()
        reposFinder.dumpResults()
    } catch (e: Exception) {
        print(e.stackTrace)
    }
}
