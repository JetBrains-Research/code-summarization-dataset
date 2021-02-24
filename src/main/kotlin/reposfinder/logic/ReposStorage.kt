package reposfinder.logic

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import reposfinder.utils.Logger
import reposfinder.utils.appendLines
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class ReposStorage(
    private val urls: List<String>,
    private val dumpDirPath: String,
    private val dumpThreshold: Int = 50,
    private val uselessFieldsSuffixes: List<String> = listOf("_url"),
    private var logger: Logger? = null
) {
    companion object {
        const val REPO_DELIMITER = "/"
        const val EXPLAIN = "explain"
        const val GOOD_REPOS_DIR = "good"
        const val BAD_REPOS_DIR = "bad"
        const val GOOD_REPOS_FILE = "good_repos.jsonl"
        const val BAD_REPOS_FILE = "bad_repos.jsonl"
        const val GOOD_INPUT_URLS = "good_input_urls.jsonl"
        const val BAD_INPUT_URLS = "bad_input_urls.jsonl"
        const val SPLIT_SIZE = 3
        const val OWNER_POS = 2
        const val NAME_POS = 1
    }

    private val objectMapper = jacksonObjectMapper()

    private val goodSummaryDir = File(dumpDirPath).resolve(GOOD_REPOS_DIR)
    private val badSummaryDir = File(dumpDirPath).resolve(BAD_REPOS_DIR)
    private val goodReposFile = File(dumpDirPath).resolve(GOOD_REPOS_FILE)
    private val badReposFile = File(dumpDirPath).resolve(BAD_REPOS_FILE)
    private val goodUrlsFile = File(dumpDirPath).resolve(GOOD_INPUT_URLS)
    private val badUrlsFile = File(dumpDirPath).resolve(BAD_INPUT_URLS)

    private val explainGoodDir = File(goodSummaryDir.absolutePath).resolve(EXPLAIN)
    private val explainBadDir = File(badSummaryDir.absolutePath).resolve(EXPLAIN)

    val goodUrls = mutableListOf<String>()
    val badUrls = mutableListOf<String>()

    val goodRepos = mutableListOf<Repository>()
    val goodBuffer = mutableListOf<Repository>()

    val badRepos = mutableListOf<Repository>()
    val badBuffer = mutableListOf<Repository>()

    val allRepos = mutableListOf<Repository>()

    val goodReposQueue = ConcurrentLinkedQueue<Repository>()

    init {
        goodSummaryDir.mkdirs()
        badSummaryDir.mkdirs()
        explainGoodDir.mkdirs()
        explainBadDir.mkdirs()
        goodReposFile.createNewFile()
        badReposFile.createNewFile()
        goodUrlsFile.createNewFile()
        badUrlsFile.createNewFile()
        initRepositories()
        goodUrlsFile.appendLines(goodUrls)
        badUrlsFile.appendLines(badUrls)
    }

    fun addGood(repo: Repository) {
        goodBuffer.add(repo)
        if (goodBuffer.size >= dumpThreshold) {
            dumpGoodAfterThreshold()
            dumpBadAfterThreshold()
        }
    }

    fun addBad(repo: Repository) {
        badBuffer.add(repo)
        if (badBuffer.size >= dumpThreshold) {
            dumpBadAfterThreshold()
            dumpGoodAfterThreshold()
        }
    }

    fun dump() {
        goodBuffer.dumpReposLinks(goodReposFile)
        goodBuffer.dumpReposSummary(goodSummaryDir)
        badBuffer.dumpReposLinks(badReposFile)
        badBuffer.dumpReposSummary(badSummaryDir)
        goodBuffer.dumpToQueue()
    }

    private fun List<Repository>.dumpToQueue() = this.forEach { repo -> goodReposQueue.add(repo) }

    private fun dumpGoodAfterThreshold() {
        goodBuffer.dumpToQueue()
        goodBuffer.dumpReposLinks(goodReposFile)
        goodBuffer.dumpReposSummary(goodSummaryDir)
        goodRepos.addAll(goodBuffer)
        logger?.add("> good repos dump: ${goodBuffer.size}")
        goodBuffer.clear()
    }

    private fun dumpBadAfterThreshold() {
        badBuffer.dumpReposLinks(badReposFile)
        badBuffer.dumpReposSummary(badSummaryDir)
        badRepos.addAll(badBuffer)
        logger?.add("> bad repos dump: ${badBuffer.size}")
        badBuffer.clear()
    }

    private fun initRepositories() = urls.forEach { url ->
        val spl = url.split(REPO_DELIMITER)
        if (spl.size >= SPLIT_SIZE) {
            val owner = spl[spl.size - OWNER_POS]
            val name = spl[spl.size - NAME_POS]
            val info = objectMapper.createObjectNode()
            if (owner.isNotEmpty() && name.isNotEmpty()) {
                allRepos.add(Repository(owner, name, null, info, logger = logger))
                goodUrls.add(url)
            } else {
                badUrls.add(url)
            }
        } else {
            badUrls.add(url)
        }
    }

    private fun List<Repository>.dumpReposLinks(file: File) =
        file.appendLines(
            this.map {
                objectMapper.valueToTree<JsonNode>(it.getDescription()).toString()
            }
        )

    private fun List<Repository>.dumpReposSummary(dir: File, clearInfo: Boolean = true) {
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT)
        for (repo in this) {
            val summaryFile = File(dir.absolutePath).resolve(repo.createSummaryName())
            val explainFile = File(dir.absolutePath).resolve(
                EXPLAIN + File.separator + repo.createSummaryName()
            )
            objectMapper.writeValue(summaryFile, repo.toJSON(objectMapper, uselessFieldsSuffixes))
            objectMapper.writeValue(explainFile, repo.toJSONExplain(objectMapper))
            // release memory (~7 KB per repo)
            if (clearInfo) {
                repo.info = objectMapper.createObjectNode()
                repo.filterResults.clear()
            }
        }
        objectMapper.disable(SerializationFeature.INDENT_OUTPUT)
    }
}
