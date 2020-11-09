package reposanalyzer.parsing

import astminer.cli.LabelExtractor
import astminer.cli.MethodNameExtractor
import reposanalyzer.config.Granularity
import reposanalyzer.config.Task
import sun.reflect.generics.reflectiveObjects.NotImplementedException

class LabelExtractorFactory {
    fun getLabelExtractor(task: Task, granularity: Granularity): LabelExtractor {
        return when (task) {
            Task.NAME -> when (granularity) {
                Granularity.METHOD -> MethodNameExtractor()
                else -> throw NotImplementedException()
            }
            else -> throw NotImplementedException()
        }
    }
}
