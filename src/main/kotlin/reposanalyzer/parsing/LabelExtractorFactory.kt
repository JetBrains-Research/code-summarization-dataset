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
        filterPredicates: List<MethodFilterPredicate>
    ): LabelExtractor {
        return when (task) {
            Task.NAME -> when (granularity) {
                Granularity.METHOD -> MethodNameExtractor(filterPredicates = filterPredicates)
                else -> throw NotImplementedError() // TODO
            }
            else -> throw NotImplementedError() // TODO
        }
    }
}
