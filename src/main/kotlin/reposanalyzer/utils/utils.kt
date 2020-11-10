package reposanalyzer.utils

import reposanalyzer.config.Language
import java.io.File
import java.util.Calendar

fun readFileToString(filePath: String): String {
    val file = File(filePath)
    if (!file.exists()) {
        return ""
    }
    return file.readText()
}

fun dotGitFilter(patches: List<String>): Pair<List<String>, List<String>> {
    val exists = mutableListOf<String>()
    val notExists = mutableListOf<String>()
    for (path in patches) {
        when (File(path + File.separator + ".git").exists()) {
            true -> exists.add(path)
            false -> notExists.add(path)
        }
    }
    return Pair(exists, notExists)
}

fun getNotHiddenNotDirectoryFiles(dirPath: String): List<File> {
    return File(dirPath).walkTopDown().filter {
        !it.isHidden && !it.isDirectory
    }.toList()
}

fun getNotHiddenNotDirectoryFiles(filesPatches: List<String>): List<File> {
    return filesPatches.map { File(it) }.filter {
        !it.isHidden && !it.isDirectory
    }
}

fun removePrefixPath(prefix: String, files: List<File>): List<String> {
    return files.map { it.absolutePath.removePrefix(prefix) }
}

fun removePrefixPath(prefix: String, filesByLang: Map<Language, List<File>>): Map<Language, List<String>> {
    val newMap = mutableMapOf<Language, List<String>>()
    filesByLang.forEach { (lang, files) ->
        newMap[lang] = removePrefixPath(prefix, files)
    }
    return newMap
}

fun absolutePatches(mainPath: String, filesPatches: List<String>): List<String> {
    return filesPatches.map { filePath ->
        mainPath + File.separator + filePath
    }
}

fun Calendar.getDateByMilliseconds(time: Long): String {
    this.timeInMillis = time
    val year = this.get(Calendar.YEAR)
    val month = this.get(Calendar.MONTH) + 1
    val day = this.get(Calendar.DAY_OF_MONTH)
    return "$year-$month-$day"
}
