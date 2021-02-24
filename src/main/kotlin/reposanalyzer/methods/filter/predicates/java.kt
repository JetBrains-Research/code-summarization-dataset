package reposanalyzer.methods.filter.predicates

import reposanalyzer.methods.MethodSummary
import java.util.function.Predicate

fun excludeWithAnnotations(annos: List<String>, annotationMaxStart: Int = 20) = Predicate<MethodSummary> { summary ->
    if (summary.body != null) {
        annos.all { anno ->
            if (summary.body!!.startsWith(anno)) {
                false
            } else {
                val idx = summary.body!!.indexOf(anno)
                idx !in 0..annotationMaxStart
            }
        }
    } else {
        true
    }
}
