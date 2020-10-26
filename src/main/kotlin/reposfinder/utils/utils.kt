package reposfinder.utils

import java.io.File

fun tokenReader(path: String): String? {
    if (File(path).exists()) {
        return File(path).readText()
    }
    return null
}
