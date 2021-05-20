package analysis.logic.summarizers

interface Summarizer : Runnable {
    var status: SummarizerStatus
}
