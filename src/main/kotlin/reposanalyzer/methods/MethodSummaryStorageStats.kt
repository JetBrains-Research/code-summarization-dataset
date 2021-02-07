package reposanalyzer.methods

import kotlin.streams.toList

class MethodSummaryStorageStats(
    set: Set<MethodIdentity>,
    val totalFiles: Int = 0,
    val pathsNumber: Int = 0
) {
    val totalMethods: Int = set.size
    val totalUniqMethodsFullNames: Int = set.parallelStream().map {
        it.methodNormalizedFullName
    }.distinct().toList().size
}
