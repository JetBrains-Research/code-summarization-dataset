package analysis.granularity.method.summarizers

import analysis.config.Language

interface MethodSummarizersFactory {
    companion object {
        fun getMethodSummarizer(
            language: Language,
            hideMethodName: Boolean,
            hiddenMethodName: String = MethodSummarizer.DEFAULT_HIDDEN_NAME
        ): MethodSummarizer {
            val methodSummarizer = when (language) {
                Language.JAVA -> JavaMethodSummarizer()
                Language.PYTHON -> PythonMethodSummarizer()
            }
            methodSummarizer.hideMethodName = hideMethodName
            methodSummarizer.hiddenMethodName = hiddenMethodName
            return methodSummarizer
        }
    }
}
