package analysis.parsing

import analysis.config.enums.SupportedLanguage
import astminer.common.model.Node
import astminer.common.model.Parser
import astminer.parse.gumtree.java.GumTreeJavaParser
import astminer.parse.gumtree.python.GumTreePythonParser

interface GumTreeParserFactory {
    companion object {
        fun getParser(language: SupportedLanguage): Parser<out Node> {
            return when (language) {
                SupportedLanguage.JAVA -> GumTreeJavaParser()
                SupportedLanguage.PYTHON -> GumTreePythonParser()
            }
        }
    }
}
