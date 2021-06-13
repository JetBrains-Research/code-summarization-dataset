package analysis.parsing

import astminer.cli.LabelExtractor
import astminer.cli.MethodFilterPredicate
import astminer.cli.MethodNameExtractor
import analysis.config.Granularity
import analysis.config.Parser
import analysis.config.Task

interface LabelExtractorFactory {

    companion object {
        fun getLabelExtractor(
            task: Task,
            granularity: Granularity,
            hideMethodName: Boolean,
            filterPredicates: List<MethodFilterPredicate>,
            parser: Parser
        ): LabelExtractor {
            return when (task) {
                Task.NAME -> when (granularity) {
                    Granularity.METHOD -> MethodNameExtractor(
                        hideMethodNames = hideMethodName,
                        filterPredicates = filterPredicates,
                        javaParser = parser.label,
                        pythonParser = parser.label
                    )
                }
            }
        }
    }
}
