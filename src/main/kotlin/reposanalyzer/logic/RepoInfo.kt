package reposanalyzer.logic

import java.io.File

data class RepoInfo(
    val path: String,
    val name: String? = null,
    val owner: String? = null
) {
    private companion object {
        const val DOT_GIT = ".git"
    }

    val dotGitPath: String
        get() = path + File.separator + DOT_GIT

    fun constructDumpPath(dumpFolder: String): String =
        dumpFolder + File.separator +
            if (owner != null) "${owner}__" else "" +
                (name ?: path.substringAfterLast(File.separator))

    override fun toString(): String {
        return "[owner: $owner, name: $name, path: $path]"
    }
}
