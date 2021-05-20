package analysis.methods.filter.predicates

import analysis.methods.MethodSummary
import java.util.function.Predicate

fun minBodyLinesLengthPredicate(minBodyLineLength: Int) = Predicate<MethodSummary> { summary ->
    if (summary.lastLineInFile != null && summary.firstLineInFile != null) {
        summary.getLinesNumber() >= minBodyLineLength
    } else {
        true
    }
}

fun excludeExactNamePredicate(excludeNames: List<String>) = Predicate<MethodSummary> { summary ->
    !excludeNames.contains(summary.name)
}

fun excludeWithNamePrefixPredicate(excludePrefixes: List<String>) = Predicate<MethodSummary> { summary ->
    excludePrefixes.all { prefix -> !summary.name.startsWith(prefix) }
}
