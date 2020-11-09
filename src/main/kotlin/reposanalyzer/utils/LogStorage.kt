package reposanalyzer.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.eclipse.jgit.revwalk.RevCommit
import java.util.Calendar

class LogStorage {
    private data class CommitInfo(
        val hash: String,
        val shortMessage: String,
        val parentsCount: Int,
        val time: Long
    )

    private val log = mutableMapOf<Pair<CommitInfo, CommitInfo>, List<String>>()

    fun add(newCommit: RevCommit?, oldCommit: RevCommit?, processedFiles: List<String>) {
        if (newCommit == null || oldCommit == null) {
            return
        }
        val newInfo = CommitInfo(
            newCommit.name,
            newCommit.shortMessage,
            newCommit.parentCount,
            newCommit.authorIdent.getWhen().time
        )
        val oldInfo = CommitInfo(
            oldCommit.name,
            oldCommit.shortMessage,
            oldCommit.parentCount,
            oldCommit.authorIdent.getWhen().time
        )
        log[Pair(newInfo, oldInfo)] = processedFiles
    }

    fun toJSON(objectMapper: ObjectMapper? = null): JsonNode {
        val mapper = objectMapper ?: jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)

        val pairs = mutableListOf<JsonNode>()
        log.forEach { (pair, files) ->
            val jsonNode = mapper.createObjectNode()

            val new = commitInfoToJSON(mapper, pair.first)
            val old = commitInfoToJSON(mapper, pair.second)

            jsonNode.set<JsonNode>("new_commit", new)
            jsonNode.set<JsonNode>("old_commit", old)
            jsonNode.set<JsonNode>("files_cnt", mapper.valueToTree(files.size))
            jsonNode.set<JsonNode>("processed_files", mapper.valueToTree(files))

            pairs.add(jsonNode)
        }
        val jsonNode = mapper.createObjectNode()
        jsonNode.set<JsonNode>("pairs_cnt", mapper.valueToTree(pairs.size))
        jsonNode.set<JsonNode>("log", mapper.valueToTree(pairs))
        return jsonNode
    }

    private fun getDate(commitInfo: CommitInfo): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = commitInfo.time
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "$year-$month-$day"
    }

    private fun commitInfoToJSON(objectMapper: ObjectMapper?, commitInfo: CommitInfo): JsonNode {
        val mapper = objectMapper ?: jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)

        val jsonNode = mapper.createObjectNode()
        jsonNode.set<JsonNode>("parents_cnt", mapper.valueToTree(commitInfo.parentsCount))
        jsonNode.set<JsonNode>("message", mapper.valueToTree(commitInfo.shortMessage))
        jsonNode.set<JsonNode>("date", mapper.valueToTree(getDate(commitInfo)))
        jsonNode.set<JsonNode>("hash", mapper.valueToTree(commitInfo.hash))

        return jsonNode
    }
}
