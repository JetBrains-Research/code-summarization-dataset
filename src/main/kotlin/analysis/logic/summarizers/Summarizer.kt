package analysis.logic.summarizers

interface Summarizer : Runnable {

    enum class Type(val label: String) {
        FILE("FILE"),
        DIR("DIRS"),
        REPO("REPO"),
        HIST("HIST")
    }

    var status: SummarizerStatus
    val type: Type
}
