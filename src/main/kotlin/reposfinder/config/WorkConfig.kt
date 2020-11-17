package reposfinder.config

data class WorkConfig(
    val dumpDir: String,
    val token: String? = null, // !!!

    // defaults
    val dumpEveryNRepos: Int = DEFAULT_DUMP_THRESHOLD,
    val sleepRange: Long = DEFAULT_SLEEP_RANGE,
    val waitsBetweenRequests: Long = DEFAULT_WAIT_TIME
) {
    private companion object {
        const val DEFAULT_DUMP_THRESHOLD = 200

        // N * 60 000 milliseconds == N * 60 seconds == N minutes
        const val DEFAULT_SLEEP_RANGE: Long = 5 * 60 * 1000

        // milliseconds
        const val DEFAULT_WAIT_TIME: Long = 500
    }
}
