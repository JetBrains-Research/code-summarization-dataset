package analysis.git

import analysis.utils.NoDotGitFolder
import analysis.utils.getDateByMilliseconds
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.util.Calendar

fun RevCommit.toJSONMain(objectMapper: ObjectMapper? = null, outerCalendar: Calendar? = null): JsonNode {
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

fun String.isDotGitPresent() =
    File(this).isDirectory && File(this).resolve(".git").exists()

fun File.isDotGitPresent() =
    this.isDirectory && this.resolve(".git").exists()

fun String.isRepoCloned(): Boolean = this.isNotEmpty() && this.isDotGitPresent()

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

fun String.openRepositoryByDotGitDir(): Repository {
    val dir = File(this)
    if (!dir.exists()) {
        throw NoDotGitFolder("no .git folder in path: $this")
    }
    return FileRepositoryBuilder()
        .readEnvironment()
        .setGitDir(dir)
        .build()
}
