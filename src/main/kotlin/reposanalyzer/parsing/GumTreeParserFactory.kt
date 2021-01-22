package reposanalyzer.parsing

import astminer.common.model.Node
import astminer.common.model.Parser
import astminer.parse.java.GumTreeJavaParser
import reposanalyzer.config.Language

interface GumTreeParserFactory {

    companion object {
        fun getParser(language: Language): Parser<out Node> {
            return when (language) {
                Language.JAVA -> GumTreeJavaParser()
            }
        }
    }
}
