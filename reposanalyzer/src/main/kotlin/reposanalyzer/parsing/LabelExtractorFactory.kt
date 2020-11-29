package reposanalyzer.parsing

import astminer.cli.LabelExtractor
import astminer.cli.MethodFilterPredicate
import astminer.cli.MethodNameExtractor
import reposanalyzer.config.Granularity
import reposanalyzer.config.Task

class LabelExtractorFactory {
    fun getLabelExtractor(
        task: Task,
        granularity: Granularity,
        hideMethodName: Boolean,
        filterPredicates: List<MethodFilterPredicate>
    ): LabelExtractor {
        return when (task) {
            Task.NAME -> when (granularity) {
                Granularity.METHOD -> MethodNameExtractor(
                    hideMethodNames = hideMethodName,
                    filterPredicates = filterPredicates
                )
                // TODO
            }
            // TODO
        }
    }
}
