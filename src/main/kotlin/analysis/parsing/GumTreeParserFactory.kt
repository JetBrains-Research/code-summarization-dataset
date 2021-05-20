package analysis.parsing

import astminer.common.model.Node
import astminer.parse.gumtree.java.GumTreeJavaParser
import astminer.parse.gumtree.python.GumTreePythonParser
import analysis.config.Language

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
