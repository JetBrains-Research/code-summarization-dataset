package reposanalyzer.methods.summarizers

import reposanalyzer.config.Language

class MethodSummarizersFactory {
    fun getMethodSummarizer(
        language: Language,
        hideMethodName: Boolean,
        hiddenMethodName: String = MethodSummarizer.DEFAULT_HIDDEN_NAME
    ): MethodSummarizer {
        val methodSummarizer = when (language) {
            Language.JAVA -> JavaMethodSummarizer()
            else -> throw NotImplementedError("no MethodSummarizer for language: ${language.label}") // TODO
        }
        methodSummarizer.hideMethodName = hideMethodName
        methodSummarizer.hiddenMethodName = hiddenMethodName
        return methodSummarizer
    }
}
