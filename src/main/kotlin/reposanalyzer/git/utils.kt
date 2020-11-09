package reposanalyzer.git

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.eclipse.jgit.revwalk.RevCommit
import java.util.Calendar

fun RevCommit.toJSON(objectMapper: ObjectMapper? = null): JsonNode {
    val mapper = objectMapper ?: jacksonObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)

    val jsonNode = mapper.createObjectNode()

    val calendar = Calendar.getInstance()
    calendar.timeInMillis = this.authorIdent.getWhen().time
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    jsonNode.set<JsonNode>("parents_cnt", mapper.valueToTree(this.parentCount))
    jsonNode.set<JsonNode>("message", mapper.valueToTree(this.shortMessage))
    jsonNode.set<JsonNode>("date", mapper.valueToTree("$year-$month-$day"))
    jsonNode.set<JsonNode>("hash", mapper.valueToTree(this.name))
    return jsonNode
}
