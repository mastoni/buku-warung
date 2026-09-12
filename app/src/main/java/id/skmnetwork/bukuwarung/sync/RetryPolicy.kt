package id.skmnetwork.bukuwarung.sync

interface RetryPolicy {
    fun calculateNextAttempt(attemptCount: Int, currentTime: Long): Long
    fun isMaxAttemptsReached(attemptCount: Int): Boolean
}

class LinearBackoffRetryPolicy(
    private val initialDelayMillis: Long = 5000L,
    private val maxAttempts: Int = 5
) : RetryPolicy {

    override fun calculateNextAttempt(attemptCount: Int, currentTime: Long): Long {
        val multiplier = attemptCount.coerceAtLeast(1)
        return currentTime + (multiplier * initialDelayMillis)
    }

    override fun isMaxAttemptsReached(attemptCount: Int): Boolean {
        return attemptCount >= maxAttempts
    }
}
