package analysis.config

import analysis.utils.BadPathException
import utils.loadJSONList
import java.io.File

fun loadPaths(pathToPathsList: String): List<String> {
    if (!File(pathToPathsList).exists()) {
        throw IllegalArgumentException("Paths to paths list doesn't exist: $pathToPathsList")
    }
    val paths = loadJSONList(pathToPathsList)
    val goodPaths = mutableSetOf<String>()
    for (path in paths) {
        if (File(path).exists()) {
            goodPaths.add(path)
        } else {
            println("INCORRECT PATH -- $path")
        }
    }
    return goodPaths.toList()
}

fun List<String>.parseRepoUrls(splitSize: Int = 2, ownerPos: Int = 2, namePos: Int = 1): List<Pair<String, String>> {
    val urls = mutableListOf<Pair<String, String>>()
    for (maybeUrl in this) {
        val spl = maybeUrl.split("/")
        if (spl.size >= splitSize) {
            val owner = spl[spl.size - ownerPos]
            val name = spl[spl.size - namePos]
            urls.add(Pair(owner, name))
        }
    }
    return urls
}

fun String.checkPathsExist(field: String) {
    if (isEmpty() || !File(this).exists()) {
        throw BadPathException("path for field `$field` doesn't exist $this")
    }
}
