package reposanalyzer.methods

import kotlin.streams.toList

class MethodSummaryStorageStats(set: Set<MethodIdentity>) {
    val totalMethods: Int
    val totalUniqMethodsFullNames: Int
    val totalFiles: Int

    init {
        totalMethods = set.size
        totalUniqMethodsFullNames = set.parallelStream().map { it.methodNormalizedFullName }.distinct().toList().size
        totalFiles = set.parallelStream().map { it.filePath }.distinct().toList().size
    }
}
