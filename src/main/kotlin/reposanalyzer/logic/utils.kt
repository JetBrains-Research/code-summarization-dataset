package reposanalyzer.logic

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import reposanalyzer.config.Language
import java.io.File

fun loadReposPatches(path: String): List<String> {
    if (!File(path).exists()) {
        println("Path doesn't exist: $path")
        return listOf()
    }
    val patches = mutableSetOf<String>()
    val objectMapper = jacksonObjectMapper()
    for (repoPath in objectMapper.readValue<List<String>>(File(path))) {
        when (File(repoPath).exists()) {
            true -> patches.add(repoPath)
            false -> println("Repository path incorrect: $repoPath")
        }
    }
    return patches.toList()
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

fun getSupportedFiles(filePatches: List<String>, supportedExtensions: List<String>): List<String> {
    return filePatches.filter { path ->
        supportedExtensions.any { ext ->
            path.endsWith(ext)
        }
    }
}
