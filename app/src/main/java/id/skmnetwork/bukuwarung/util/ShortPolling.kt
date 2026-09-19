package id.skmnetwork.bukuwarung.util

import kotlinx.coroutines.delay

suspend fun <T> boundedPoll(
    maxAttempts: Int = 12,
    intervalMs: Long = 5000,
    maxDurationMs: Long = 60000,
    action: suspend () -> T,
    shouldContinue: (T) -> Boolean
): T {
    val startTime = System.currentTimeMillis()
    var attempt = 0
    var result: T? = null
    while (attempt < maxAttempts) {
        if (System.currentTimeMillis() - startTime > maxDurationMs) break
        result = action()
        if (!shouldContinue(result)) return result
        attempt++
        if (attempt < maxAttempts) {
            delay(intervalMs)
        }
    }
    return result!!
}