package reposanalyzer.logic

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import reposanalyzer.git.constructRepoLoadUrl
import reposanalyzer.git.getDefaultBranch
import reposanalyzer.git.getFirstParentHistory
import reposanalyzer.git.getMergeCommitsHistory
import reposanalyzer.git.isDotGitPresent
import reposanalyzer.git.isRepoCloned
import reposanalyzer.git.openRepositoryByDotGitDir
import reposanalyzer.git.tryCloneRepositoryNTimes
import java.io.File
import java.io.FileOutputStream

class AnalysisRepository(
    var path: String = "",
    var owner: String? = null,
    var name: String? = null
) {
    private companion object {
        const val CLONE_TRIES_NUMBER = 2
        const val TIME_BETWEEN_TRIES = 5000L
        const val LOADED_REPO = "LOADED_REPO"
        const val DOT_GIT = ".git"
    }

    lateinit var git: Git
    lateinit var repository: Repository
    var defaultBranchHead: Ref? = null

    val mergeCommits = mutableListOf<RevCommit>()
    val firstParentsCommits = mutableListOf<RevCommit>()

    var mergeCommitsNumber: Int = 0
    var firstParentsCommitsNumber: Int = 0

    val mergesPart: Float
        get() = if (firstParentsCommitsNumber == 0) 0.0f else mergeCommitsNumber.toFloat() / firstParentsCommitsNumber

    @Volatile var isLoaded = false

    fun initRepository(dumpPath: String): Boolean =
        if (path.isRepoCloned()) {
            openRepositoryByDotGitDir()
        } else {
            cloneRepository(dumpPath)
        }

    fun cloneRepository(rootPath: String): Boolean {
        if (!path.isRepoCloned()) {
            val url = constructRepoLoadUrl(owner, name) ?: return false
            path = File(rootPath).resolve(LOADED_REPO).absolutePath
            val dir = File(path)
            if (dir.exists() && dir.isDotGitPresent()) {
                FileUtils.deleteDirectory(dir)
            }
            git = tryCloneRepositoryNTimes(url, dir, CLONE_TRIES_NUMBER, TIME_BETWEEN_TRIES) ?: return false
            repository = git.repository
            isLoaded = true
        }
        return true
    }

    fun openRepositoryByDotGitDir(): Boolean {
        if (!path.isRepoCloned()) {
            return false
        }
        repository = File(path).resolve(DOT_GIT).absolutePath.openRepositoryByDotGitDir()
        git = Git(repository)
        isLoaded = true
        return true
    }

    fun loadDefaultBranchHead(): Boolean {
        defaultBranchHead = repository.getDefaultBranch() ?: return false
        return true
    }

    fun loadCommitsHistory() {
        defaultBranchHead?.let {
            mergeCommits.addAll(repository.getMergeCommitsHistory(it.objectId, includeYoungest = true))
            mergeCommitsNumber = mergeCommits.size
            firstParentsCommits.addAll(repository.getFirstParentHistory(it.objectId))
            firstParentsCommitsNumber = firstParentsCommits.size
        }
    }

    fun constructDumpPath(dumpFolder: String): String {
        var dumpPath = dumpFolder + File.separator
        dumpPath += if (owner != null) "${owner}__" else ""
        dumpPath += (name ?: path.substringAfterLast(File.separator))
        return dumpPath
    }

    fun clear() {
        mergeCommits.clear()
        firstParentsCommits.clear()
        git.close()
    }

    fun dump(dumpPath: String, objectMapper: ObjectMapper? = null) {
        val mapper = objectMapper ?: jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
        mapper.writeValue(FileOutputStream(File(dumpPath), false), toJSON(mapper))
    }

    fun toJSON(objectMapper: ObjectMapper? = null): JsonNode {
        val mapper = objectMapper ?: jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
        val jsonNode = mapper.createObjectNode()
        jsonNode.set<JsonNode>("owner", mapper.valueToTree(owner))
        jsonNode.set<JsonNode>("name", mapper.valueToTree(name))
        jsonNode.set<JsonNode>("default_branch", mapper.valueToTree(defaultBranchHead?.name))
        jsonNode.set<JsonNode>("merge_commits_cnt", mapper.valueToTree(mergeCommitsNumber))
        jsonNode.set<JsonNode>("first_parents_commits_cnt", mapper.valueToTree(firstParentsCommitsNumber))
        jsonNode.set<JsonNode>("merges_part", mapper.valueToTree(mergesPart))
        return jsonNode
    }

    override fun toString(): String = "[owner: $owner, name: $name, path: $path]"
}
