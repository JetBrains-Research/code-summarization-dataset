package analysis.parsing

import astminer.cli.LabelExtractor
import astminer.cli.MethodFilterPredicate
import astminer.cli.MethodNameExtractor
import analysis.config.Granularity
import analysis.config.Task

interface LabelExtractorFactory {

    companion object {
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
                        filterPredicates = filterPredicates,
                        javaParser = "gumtree",
                        pythonParser = "gumtree"
                    )
                    // TODO
                }
                // TODO
            }
        }
    }
}
