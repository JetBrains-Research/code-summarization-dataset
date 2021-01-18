package reposanalyzer.git

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import reposanalyzer.utils.getDateByMilliseconds
import java.io.File
import java.util.Calendar

fun RevCommit.getCommitInfo(): CommitInfo = CommitInfo(
    this.parentCount,
    this.shortMessage,
    this.authorIdent.getWhen().time, // milliseconds
    this.authorIdent.name,
    this.authorIdent.emailAddress,
    this.name // hash
)

fun RevCommit.toJSON(objectMapper: ObjectMapper? = null, outerCalendar: Calendar? = null): JsonNode {
    val mapper = objectMapper ?: jacksonObjectMapper()
    val calendar = outerCalendar ?: Calendar.getInstance()

    val jsonNode = mapper.createObjectNode()
    val date = calendar.getDateByMilliseconds(this.authorIdent.getWhen().time)

    jsonNode.set<JsonNode>("parents_cnt", mapper.valueToTree(this.parentCount))
    jsonNode.set<JsonNode>("message", mapper.valueToTree(this.shortMessage))
    jsonNode.set<JsonNode>("date", mapper.valueToTree(date))
    jsonNode.set<JsonNode>("author_name", mapper.valueToTree(this.authorIdent.name))
    jsonNode.set<JsonNode>("author_email", mapper.valueToTree(this.authorIdent.emailAddress))
    jsonNode.set<JsonNode>("hash", mapper.valueToTree(this.name))
    return jsonNode
}

fun constructRepoLoadUrl(owner: String?, name: String?): String? =
    if (owner == null || name == null) {
        null
    } else {
        "https://github.com/$owner/$name"
    }

fun tryCloneRepositoryNTimes(url: String, cloneDir: File, n: Int, sleepTime: Long): Git? {
    for (i in 1..n) {
        val git = tryCloneRepository(url, cloneDir)
        if (git != null) {
            return git
        }
        try {
            Thread.sleep(sleepTime)
        } catch (e: InterruptedException) {
            // ignore
        }
    }
    return null
}

fun tryCloneRepository(url: String, cloneDir: File): Git? =
    try {
        Git.cloneRepository()
            .setURI(url)
            .setDirectory(cloneDir)
            .setCloneAllBranches(true)
            .call()
    } catch (e: Exception) {
        null
    }
