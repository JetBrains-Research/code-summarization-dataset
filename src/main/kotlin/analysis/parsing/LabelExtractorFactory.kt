package analysis.parsing

import analysis.config.enums.Granularity
import analysis.config.enums.SupportedParser
import analysis.config.enums.Task
import astminer.cli.LabelExtractor
import astminer.cli.MethodFilterPredicate
import astminer.cli.MethodNameExtractor

interface LabelExtractorFactory {
    companion object {
        fun getLabelExtractor(
            task: Task,
            granularity: Granularity,
            hideMethodName: Boolean,
            filterPredicates: List<MethodFilterPredicate>,
            parser: SupportedParser
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
