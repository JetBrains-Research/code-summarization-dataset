package filtration.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import filtration.utils.NoTokenException
import filtration.utils.NoUrlsException
import java.io.File

fun String.readToken(): String {
    val file = File(this)
    if (!file.exists()) {
        throw NoTokenException("file with token doesn't exist")
    }
    val token = file.readText()
    if (token.isEmpty()) {
        throw NoTokenException("file with token is empty")
    }
    return token.trimEnd()
}

fun String.readUrls(): List<String> {
    val file = File(this)
    if (!file.exists()) {
        throw NoUrlsException("file with repos urls doesn't exist")
    }
    return jacksonObjectMapper().readValue(file)
}
