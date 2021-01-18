package reposanalyzer.utils

import reposanalyzer.config.Language
import java.io.File
import java.util.Calendar

fun String.readFileToString(): String {
    val file = File(this)
    if (!file.exists()) {
        return ""
    }
    return file.readText()
}

fun dotGitFilter(patches: List<String>): Pair<List<String>, List<String>> {
    val exists = mutableListOf<String>()
    val notExists = mutableListOf<String>()
    patches.forEach { path ->
        if (path.isDotGitPresent()) {
            exists.add(path)
        } else {
            notExists.add(path)
        }
    }
    return Pair(exists, notExists)
}

fun String.isDotGitPresent() =
    File(this).isDirectory && File(this).resolve(".git").exists()

fun File.isDotGitPresent() =
    this.isDirectory && this.resolve(".git").exists()

fun getNotHiddenNotDirectoryFiles(dirPath: String): List<File> =
    File(dirPath).walkTopDown().filter { !it.isHidden && !it.isDirectory }.toList()

fun getNotHiddenNotDirectoryFiles(filesPatches: List<String>): List<File> =
    filesPatches.map { File(it) }.filter { !it.isHidden && !it.isDirectory }.toList()

fun List<String>.getAbsolutePatches(mainPath: String): List<String> =
    this.map { filePath -> mainPath + File.separator + filePath }

fun removePrefixPath(prefix: String, files: List<File>): List<String> =
    files.map { it.absolutePath.removePrefix(prefix) }

fun Map<Language, List<File>>.removePrefixPath(prefix: String): Map<Language, List<String>> {
    val newMap = mutableMapOf<Language, List<String>>()
    this.forEach { (lang, files) ->
        newMap[lang] = removePrefixPath(prefix, files)
    }
    return newMap
}

fun Calendar.getDateByMilliseconds(time: Long): String {
    this.timeInMillis = time
    val year = this.get(Calendar.YEAR)
    val month = this.get(Calendar.MONTH) + 1
    val day = this.get(Calendar.DAY_OF_MONTH)
    return "$year-$month-$day"
}
