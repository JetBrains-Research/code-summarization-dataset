package analysis.logic

import analysis.config.Language
import analysis.config.Parser
import analysis.parsing.GumTreeParserFactory
import analysis.parsing.SafeParser
import astminer.common.model.Node
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
    val parsers: ConcurrentHashMap<Parser, ConcurrentHashMap<Language, SafeParser<out Node>>>
) {
    companion object {
        fun construct(threadsNumber: Int, parser: Parser, languages: List<Language>): ParseEnvironment {
            val pool = Executors.newFixedThreadPool(threadsNumber)
            val parsers = ConcurrentHashMap<Parser, ConcurrentHashMap<Language, SafeParser<out Node>>>()
            parsers[parser] = ConcurrentHashMap<Language, SafeParser<out Node>>()
            for (lang in languages) {
                when (parser) {
                    Parser.GUMTREE -> parsers[parser]?.set(lang, GumTreeParserFactory.getParser(lang))
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
