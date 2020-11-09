package reposanalyzer.utils

import reposanalyzer.config.Language
import java.io.File

fun readFileToString(filePath: String): String {
    val file = File(filePath)
    if (!file.exists()) {
        return ""
    }
    return file.readText()
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

fun getFilesByLanguage(files: List<File>, languages: List<Language>): Map<Language, List<File>> {
    val filesByLang = mutableMapOf<Language, MutableList<File>>()
    languages.forEach { lang ->
        filesByLang[lang] = mutableListOf()
    }
    files.forEach { file ->
        inner@for (lang in languages) {
            if (isFileFromLanguage(file, lang)) {
                filesByLang[lang]?.add(file)
                break@inner
            }
        }
    }
    return filesByLang
}

fun isFileFromLanguage(file: File, language: Language): Boolean {
    return language.extensions.any { ext ->
        file.absolutePath.endsWith(ext)
    }
}

fun getProjectDir(): String {
    return System.getProperty("user.dir")
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
