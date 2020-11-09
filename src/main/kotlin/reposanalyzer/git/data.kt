package reposanalyzer.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

fun cloneRepository(repoURL: String, repoDir: String): Git? {
    return Git.cloneRepository()
        .setCloneAllBranches(true)
        .setDirectory(File(repoDir))
        .setURI(repoURL)
        .call()
}

fun openRepositoryByRepoDir(repoDirPath: String): Repository? {
    val dir = File(repoDirPath)
    return when (dir.exists()) {
        false -> null
        true ->
            FileRepositoryBuilder()
                .readEnvironment()
                .setWorkTree(dir)
                .findGitDir()
                .build()
    }
}

fun openRepositoryByDotGitDir(dotGitDirPath: String): Repository? {
    val dir = File(dotGitDirPath)
    return when (dir.exists()) {
        false -> null
        true ->
            FileRepositoryBuilder()
                .readEnvironment()
                .setGitDir(dir)
                .build()
    }
}
