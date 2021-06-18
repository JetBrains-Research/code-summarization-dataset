package analysis.utils

open class AnalysisConfigException(message: String) : Exception(message)

class MissingConfigFieldsException(message: String) : AnalysisConfigException(message)

class BadMethodUniquenessConfigParameter(message: String) : AnalysisConfigException(message)

class UnsupportedLanguage(message: String) : AnalysisConfigException(message)

class BadPathException(message: String) : Exception(message)

class NoDotGitFolder(message: String) : Exception(message)

