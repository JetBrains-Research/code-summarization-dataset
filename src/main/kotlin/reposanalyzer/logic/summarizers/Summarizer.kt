package reposanalyzer.logic.summarizers

interface Summarizer : Runnable {
    var status: SummarizerStatus
}
