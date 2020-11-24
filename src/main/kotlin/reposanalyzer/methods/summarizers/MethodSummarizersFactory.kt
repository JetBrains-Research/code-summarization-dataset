package reposanalyzer.methods.summarizers

import reposanalyzer.config.Language

class MethodSummarizersFactory {
    fun getMethodSummarizer(language: Language): MethodSummarizer =
        when (language) {
            Language.JAVA -> JavaMethodSummarizer()
            else -> throw NotImplementedError("no MethodSummarizer for language: ${language.label}") // TODO
        }
}
