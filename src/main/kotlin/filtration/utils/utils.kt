package filtration.utils

import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun File.appendLines(lines: List<String>) =
    FileOutputStream(this, true).bufferedWriter().use { out ->
        lines.forEach {
            out.appendLine(it)
        }
    }

fun prettyDate(millis: Long): String {
    val date = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    return DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy").format(date)
}
