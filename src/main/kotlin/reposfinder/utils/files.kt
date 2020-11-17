package reposfinder.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

fun String.readToken(): String? {
    val token = File(this).readText()
    if (token.isEmpty()) {
        println("File token is empty!")
        return null
    }
    return token
}

fun String.readList(): List<String> {
    val jsonMapper = jacksonObjectMapper()
    return jsonMapper.readValue(File(this))
}
