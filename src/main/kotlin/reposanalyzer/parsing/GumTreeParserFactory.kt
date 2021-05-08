package reposanalyzer.parsing

import astminer.common.model.Node
import astminer.parse.java.GumTreeJavaParser
import astminer.parse.python.GumTreePythonParser
import reposanalyzer.config.Language

interface GumTreeParserFactory {

    companion object {
        fun getParser(language: Language): SafeParser<out Node> {
            return when (language) {
                Language.JAVA -> SafeParser(GumTreeJavaParser())
                Language.PYTHON -> SafeParser(GumTreePythonParser())
            }
        }
    }
}
