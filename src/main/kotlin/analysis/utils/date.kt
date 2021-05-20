package analysis.utils

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

fun prettyDate(millis: Long): String {
    val date = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    return DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy").format(date)
}

fun Calendar.getDateByMilliseconds(time: Long): String {
    this.timeInMillis = time
    val year = this.get(Calendar.YEAR)
    val month = this.get(Calendar.MONTH) + 1
    val day = this.get(Calendar.DAY_OF_MONTH)
    return "$year-$month-$day"
}
