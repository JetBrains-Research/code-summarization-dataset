package reposanalyzer.logic

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.eclipse.jgit.revwalk.RevCommit
import reposanalyzer.config.Language
import reposanalyzer.git.CommitInfo
import reposanalyzer.git.getCommitInfo
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

/*
 *  Log storage with line-delimited ('\n') JSON dumps:
 *      1. after dumpThreshold
 *      2. by explicit dumpData method call
 */
class LogStorage(
    private val dumpFilePath: String,
    private val dumpThreshold: Int = 500
) {
    private val log = mutableMapOf<Pair<CommitInfo, CommitInfo>, Map<Language, List<String>>>()
    private val dumpFile: File = File(dumpFilePath)
    private val calendar = Calendar.getInstance()
    private val objectMapper = jacksonObjectMapper()

    init {
        dumpFile.createNewFile()
    }

    fun add(newCommit: RevCommit?, oldCommit: RevCommit?, filesByLang: Map<Language, List<String>>) {
        if (newCommit == null || oldCommit == null) {
            return
        }
        val newInfo = newCommit.getCommitInfo()
        val oldInfo = oldCommit.getCommitInfo()
        log[Pair(newInfo, oldInfo)] = filesByLang
        if (log.size > dumpThreshold) {
            dumpData()
        }
    }

    fun dumpData() {
        FileOutputStream(dumpFile, true).bufferedWriter().use { out ->
            toJSON().forEach { jsonNode ->
                out.appendLine(jsonNode.toString())
            }
        }
        log.clear() // clear log after dump
    }

    private fun toJSON(): List<JsonNode> {
        val pairs = mutableListOf<JsonNode>()
        log.forEach { (pair, filesByLang) ->
            val jsonNode = objectMapper.createObjectNode()
            val filesNode = objectMapper.createObjectNode()
            var filesCnt = 0
            for ((lang, files) in filesByLang) {
                if (files.isNotEmpty()) {
                    filesCnt += files.size
                    filesNode.set<JsonNode>(lang.label, objectMapper.valueToTree(files))
                }
            }
            val new = pair.first.toJSON(objectMapper, calendar)
            val old = pair.second.toJSON(objectMapper, calendar)
            jsonNode.set<JsonNode>("new_commit", new)
            jsonNode.set<JsonNode>("old_commit", old)
            jsonNode.set<JsonNode>("files_cnt", objectMapper.valueToTree(filesCnt))
            jsonNode.set<JsonNode>("processed_files", filesNode)
            pairs.add(jsonNode)
        }
        return pairs
    }
}
