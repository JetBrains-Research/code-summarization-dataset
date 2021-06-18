package analysis.granularity

import analysis.config.AnalysisConfig
import analysis.config.enums.Task
import analysis.granularity.method.MethodSummaryStorage
import com.fasterxml.jackson.databind.JsonNode
import utils.FileLogger

interface SummaryStorage {
    companion object {
        fun get(
            dumpFolder: String,
            config: AnalysisConfig,
            logger: FileLogger? = null
        ): SummaryStorage {
            return when (config.task) {
                Task.NAME -> MethodSummaryStorage(
                    dumpFolder,
                    config.identityConfig,
                    logger,
                    isCode2SeqDump = config.isCode2SeqDump,
                    isAstDotFormat = config.isAstDotFormat,
                    summaryDumpThreshold = config.summaryDumpThreshold
                )
            }
        }
    }

    fun add(summary: Summary): Boolean
    fun dump()
    fun clear()
    fun stats(): JsonNode
}
