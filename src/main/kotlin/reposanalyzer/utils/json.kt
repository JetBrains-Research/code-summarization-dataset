package reposanalyzer.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

fun loadReposPatches(path: String): List<String> {
    if (!File(path).exists()) {
        println("Path doesn't exist: $path")
        return listOf()
    }
    val patches = mutableListOf<String>()
    val objectMapper = jacksonObjectMapper()
    for (repoPath in objectMapper.readValue<List<String>>(File(path))) {
        when (File(repoPath).exists()) {
            true -> patches.add(repoPath)
            false -> println("Repository path incorrect: $repoPath")
        }
    }
    return patches
}
