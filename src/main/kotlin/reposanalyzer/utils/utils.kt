package reposanalyzer.utils

import org.apache.commons.io.FileUtils
import reposanalyzer.config.Language
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

fun File.clearFile() = FileOutputStream(this, false).bufferedWriter()

fun String.readFileToString(): String {
    val file = File(this)
    if (!file.exists()) {
        return ""
    }
    return file.readText()
}

fun String.deleteDirectory() =
    try {
        FileUtils.deleteDirectory(File(this))
    } catch (e: IOException) {
        // ignore
    }

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
