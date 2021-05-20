package analysis.logic

import analysis.config.Language
import java.io.File

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

fun String.getFileLinesLength(): List<Int> {
    val list = mutableListOf<Int>()
    File(this).forEachLine {
        list.add(it.length)
    }
    return list
}

fun List<Int>.calculateLinesStarts(): List<Int> {
    var sum = 0
    val starts = mutableListOf<Int>()
    starts.add(sum)
    this.forEach {
        sum += it + 1
        starts.add(sum)
    }
    return starts
}

fun List<Int>.whichLine(pos: Int): Int = kotlin.math.abs(this.binarySearch(pos) + 1)

fun String.splitToParents() = this.split(File.separator).filter { it.isNotEmpty() }
