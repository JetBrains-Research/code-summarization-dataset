package reposanalyzer.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import reposanalyzer.utils.AnalysisConfigException
import java.io.File

fun loadJSONList(path: String): List<String> {
    val objectMapper = jacksonObjectMapper()
    return objectMapper.readValue(File(path))
}

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

fun checkFileExists(path: String, isFile: Boolean = true, message: String? = null) {
    val file = File(path)
    if (!file.exists()) {
        val errMsg = (if (message != null) "$message: " else "") + "file doesn't exist: $path"
        throw AnalysisConfigException(errMsg)
    }
    if (isFile && !file.isFile) {
        val errMsg = (if (message != null) "$message: " else "") + "not file: $path"
        throw AnalysisConfigException(errMsg)
    } else if (!isFile && !file.isDirectory) {
        val errMsg = (if (message != null) "$message: " else "") + "not directory: $path"
        throw AnalysisConfigException(errMsg)
    }
}
