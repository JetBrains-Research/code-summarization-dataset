package reposanalyzer.logic

import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import reposanalyzer.git.constructRepoLoadUrl
import reposanalyzer.git.getDefaultBranch
import reposanalyzer.git.openRepositoryByDotGitDir
import reposanalyzer.git.tryCloneRepositoryNTimes
import reposanalyzer.utils.isDotGitPresent
import java.io.File

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

    var defaultBranchHead: Ref? = null
    lateinit var repository: Repository
    lateinit var git: Git

    @Volatile var isLoaded = false

    fun cloneRepository(rootPath: String): Boolean {
        if (isRepoCloned()) {
            return true
        }
        val url = constructRepoLoadUrl(owner, name) ?: return false
        path = File(rootPath).resolve(LOADED_REPO).absolutePath
        val dir = File(path)
        if (dir.exists() && dir.isDotGitPresent()) {
            FileUtils.deleteDirectory(dir)
        }
        git = tryCloneRepositoryNTimes(url, dir, CLONE_TRIES_NUMBER, TIME_BETWEEN_TRIES) ?: return false
        repository = git.repository
        defaultBranchHead = repository.getDefaultBranch() ?: return false
        isLoaded = true
        return true
    }

    fun openRepositoryByDotGitDir(): Boolean {
        if (!isRepoCloned()) {
            return false
        }
        repository = File(path).resolve(DOT_GIT).absolutePath.openRepositoryByDotGitDir()
        git = Git(repository)
        defaultBranchHead = repository.getDefaultBranch() ?: return false
        isLoaded = true
        return true
    }

    fun isRepoCloned(): Boolean =
        path.isNotEmpty() && path.isDotGitPresent()

    fun constructDumpPath(dumpFolder: String): String {
        var dumpPath = dumpFolder + File.separator
        dumpPath += if (owner != null) "${owner}__" else ""
        dumpPath += (name ?: path.substringAfterLast(File.separator))
        return dumpPath
    }

    override fun toString(): String = "[owner: $owner, name: $name, path: $path]"
}
