package utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

fun loadJSON(path: String): JsonNode = jacksonObjectMapper().readValue(File(path))

fun loadJSONList(path: String): List<String> = jacksonObjectMapper().readValue(File(path))
