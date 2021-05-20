package analysis.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import analysis.utils.NoDotGitFolder
import java.io.File

fun cloneRepository(repoURL: String, repoDir: String): Git? = Git.cloneRepository()
    .setCloneAllBranches(true)
    .setDirectory(File(repoDir))
    .setURI(repoURL)
    .call()

fun String.openRepositoryByRepoDir(repoDirPath: String): Repository {
    val dir = File(repoDirPath)
    if (!dir.exists()) {
        throw NoDotGitFolder("no .git folder in path: $this")
    }
    return FileRepositoryBuilder()
        .readEnvironment()
        .setWorkTree(dir)
        .findGitDir()
        .build()
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
