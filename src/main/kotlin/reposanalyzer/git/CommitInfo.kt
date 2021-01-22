package reposanalyzer.git

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import reposanalyzer.utils.getDateByMilliseconds
import java.util.Calendar

data class CommitInfo(
    val parentsCount: Int,
    val message: String,
    val time: Long,
    val authorName: String,
    val authorEmail: String,
    val hash: String
) {
    fun toJSON(objectMapper: ObjectMapper? = null, outerCalendar: Calendar? = null): JsonNode {
        val mapper = objectMapper ?: jacksonObjectMapper()
        val calendar = outerCalendar ?: Calendar.getInstance()

        val jsonNode = mapper.createObjectNode()
        val date = calendar.getDateByMilliseconds(time)
        jsonNode.set<JsonNode>("parents_cnt", mapper.valueToTree(parentsCount))
        jsonNode.set<JsonNode>("message", mapper.valueToTree(message))
        jsonNode.set<JsonNode>("date", mapper.valueToTree(date))
        jsonNode.set<JsonNode>("author_name", mapper.valueToTree(authorName))
        jsonNode.set<JsonNode>("author_email", mapper.valueToTree(authorEmail))
        jsonNode.set<JsonNode>("hash", mapper.valueToTree(hash))

        return jsonNode
    }
}
