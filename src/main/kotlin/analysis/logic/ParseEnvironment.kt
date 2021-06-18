package analysis.logic

import analysis.config.enums.SupportedLanguage
import analysis.config.enums.SupportedParser
import analysis.parsing.GumTreeParserFactory
import astminer.common.model.Node
import astminer.common.model.Parser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import java.lang.Exception
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class ParseEnvironment(
    val pool: ExecutorService,
    val parsers: ConcurrentHashMap<SupportedParser, ConcurrentHashMap<SupportedLanguage, Parser<out Node>>>
) {
    companion object {
        fun construct(
            threadsNumber: Int,
            parser: SupportedParser,
            languages: List<SupportedLanguage>
        ): ParseEnvironment {
            val pool = Executors.newFixedThreadPool(threadsNumber)
            val parsers = ConcurrentHashMap<SupportedParser, ConcurrentHashMap<SupportedLanguage, Parser<out Node>>>()
            parsers[parser] = ConcurrentHashMap<SupportedLanguage, Parser<out Node>>()
            for (lang in languages) {
                when (parser) {
                    SupportedParser.GUMTREE -> parsers[parser]?.set(lang, GumTreeParserFactory.getParser(lang))
                }
            }
            return ParseEnvironment(pool, parsers)
        }
    }

    @Volatile var isInterrupted = false
        private set

    val dispatcher: CoroutineDispatcher = pool.asCoroutineDispatcher()

    fun shutDown() {
        if (!isInterrupted) {
            isInterrupted = true
            dispatcher.cancel(CancellationException("analysis was interrupted"))
            try {
                pool.shutdownNow()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
