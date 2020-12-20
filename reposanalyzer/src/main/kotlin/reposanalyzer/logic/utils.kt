package reposanalyzer.logic

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import reposanalyzer.config.Language
import java.io.File

fun loadReposPatches(path: String): List<String> {
    if (!File(path).exists()) {
        throw IllegalArgumentException("Path to repos dirs doesn't exist: $path")
    }
    val patches = mutableSetOf<String>()
    val objectMapper = jacksonObjectMapper()
    for (repoPath in objectMapper.readValue<List<String>>(File(path))) {
        if (File(repoPath).exists()) {
            patches.add(repoPath)
        } else {
            println("Repository path incorrect: $repoPath")
        }
    }
    return patches.toList()
}

fun List<File>.getFilesByLanguage(languages: List<Language>): Map<Language, List<File>> {
    val filesByLang = mutableMapOf<Language, MutableList<File>>()
    languages.forEach { lang ->
        filesByLang[lang] = mutableListOf()
    }
    this.forEach { file ->
        inner@for (lang in languages) {
            if (isFileFromLanguage(file, lang)) {
                filesByLang[lang]?.add(file)
                break@inner
            }
        }
    }
    return filesByLang
}

fun isFileFromLanguage(file: File, language: Language): Boolean = language.extensions.any { ext ->
    file.absolutePath.endsWith(ext)
}

fun List<String>.getSupportedFiles(supportedExtensions: List<String>): List<String> =
    this.filter { path -> supportedExtensions.any { ext -> path.endsWith(ext) } }
