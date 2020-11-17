package reposfinder.logic

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.FileOutputStream

class ReposStorage(
    private val urls: List<String>,
    private val dumpDirPath: String,
    private val dumpThreshold: Int = 200,
    private val uselessFieldsSuffixes: List<String> = listOf("_url")
) {
    companion object {
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
        .enable(SerializationFeature.INDENT_OUTPUT)

    private val goodSummaryDir = File(dumpDirPath + File.separator + GOOD_REPOS_DIR)
    private val badSummaryDir = File(dumpDirPath + File.separator + BAD_REPOS_DIR)
    private val explainGoodDir = File(goodSummaryDir.absolutePath + File.separator + EXPLAIN)
    private val explainBadDir = File(badSummaryDir.absolutePath + File.separator + EXPLAIN)
    private val goodReposFile = File(dumpDirPath + File.separator + GOOD_REPOS_FILE)
    private val badReposFile = File(dumpDirPath + File.separator + BAD_REPOS_FILE)
    private val goodUrlsFile = File(dumpDirPath + File.separator + GOOD_INPUT_URLS)
    private val badUrlsFile = File(dumpDirPath + File.separator + BAD_INPUT_URLS)

    val goodUrls = mutableListOf<String>()
    val badUrls = mutableListOf<String>()

    val goodRepos = mutableListOf<Repository>()
    val goodBuffer = mutableListOf<Repository>()

    val badRepos = mutableListOf<Repository>()
    val badBuffer = mutableListOf<Repository>()

    val allRepos = mutableListOf<Repository>()

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
        goodUrls.dumpUrls(goodUrlsFile)
        badUrls.dumpUrls(badUrlsFile)
    }

    fun addGood(repo: Repository) {
        goodBuffer.add(repo)
        if (goodBuffer.size >= dumpThreshold) {
            goodBuffer.dumpReposLinks(goodReposFile)
            goodBuffer.dumpReposSummary(goodSummaryDir)
            goodRepos.addAll(goodBuffer)
            goodBuffer.clear()
        }
    }

    fun addBad(repo: Repository) {
        badBuffer.add(repo)
        if (badBuffer.size >= dumpThreshold) {
            badBuffer.dumpReposLinks(badReposFile)
            badBuffer.dumpReposSummary(badSummaryDir)
            badRepos.addAll(badBuffer)
            badBuffer.clear()
        }
    }

    fun dump() {
        goodBuffer.dumpReposLinks(goodReposFile)
        goodBuffer.dumpReposSummary(goodSummaryDir)
        badBuffer.dumpReposLinks(badReposFile)
        badBuffer.dumpReposSummary(badSummaryDir)
    }

    private fun initRepositories() {
        urls.forEach { url ->
            val spl = url.split("/")
            if (spl.size >= SPLIT_SIZE) {
                val owner = spl[spl.size - OWNER_POS]
                val name = spl[spl.size - NAME_POS]
                val info = objectMapper.createObjectNode()
                if (owner.isNotEmpty() && name.isNotEmpty()) {
                    allRepos.add(Repository(owner, name, info))
                    goodUrls.add(url)
                } else {
                    badUrls.add(url)
                }
            } else {
                badUrls.add(url)
            }
        }
    }

    private fun List<Repository>.dumpReposLinks(file: File) {
        objectMapper.disable(SerializationFeature.INDENT_OUTPUT)
        FileOutputStream(file, true).bufferedWriter().use { out ->
            this.forEach { repo ->
                val node = objectMapper.valueToTree<JsonNode>(repo.getDescription())
                out.appendLine(node.toString())
            }
        }
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT)
    }

    private fun List<String>.dumpUrls(file: File) {
        objectMapper.disable(SerializationFeature.INDENT_OUTPUT)
        FileOutputStream(file, true).bufferedWriter().use { out ->
            this.forEach {
                out.appendLine(it)
            }
        }
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT)
    }

    private fun List<Repository>.dumpReposSummary(dir: File, clearInfo: Boolean = true) {
        for (repo in this) {
            val path = dir.absolutePath + File.separator + "${repo.owner}__${repo.name}.json" // 2 __
            val explainPath = dir.absolutePath + File.separator + EXPLAIN +
                File.separator + "${repo.owner}__${repo.name}.json"
            objectMapper.writeValue(
                File(path),
                repo.toJSON(objectMapper, uselessFieldsSuffixes)
            )
            objectMapper.writeValue(
                File(explainPath),
                repo.toJSONExplain(objectMapper)
            )
            // release memory (~7 KB per repo)
            if (clearInfo) {
                repo.info = objectMapper.createObjectNode()
            }
        }
    }
}
