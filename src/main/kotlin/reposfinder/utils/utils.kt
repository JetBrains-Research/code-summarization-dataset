package reposfinder.utils

import java.io.File
import java.io.FileOutputStream

fun File.appendLines(lines: List<String>) =
    FileOutputStream(this, true).bufferedWriter().use { out ->
        lines.forEach {
            out.appendLine(it)
        }
    }
