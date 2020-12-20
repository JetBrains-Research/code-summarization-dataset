package reposanalyzer.logic

import java.io.File

data class RepoInfo(
    var path: String,
    val name: String? = null,
    val owner: String? = null
) {
    private companion object {
        const val DOT_GIT = ".git"
        const val GIT_URL = "https://github.com"
    }

    val dotGitPath: String
        get() = path + File.separator + DOT_GIT

    fun constructDumpPath(dumpFolder: String): String {
        var dumpPath = dumpFolder + File.separator
        dumpPath += if (owner != null) "${owner}__" else ""
        dumpPath += (name ?: this.path.substringAfterLast(File.separator))
        return dumpPath
    }

    fun constructLoadUrl(): String? =
        if (owner == null || name == null) {
            null
        } else {
            "$GIT_URL/$owner/$name"
        }

    override fun toString(): String = "[owner: $owner, name: $name, path: $path]"
}
