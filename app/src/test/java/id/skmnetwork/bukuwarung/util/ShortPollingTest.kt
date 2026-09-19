package id.skmnetwork.bukuwarung.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortPollingTest {

    @Test
    fun boundedPoll_stopsOnTerminalCondition() = runBlocking {
        var attempts = 0
        val result = boundedPoll(
            maxAttempts = 10,
            intervalMs = 10,
            maxDurationMs = 5000,
            action = {
                attempts++
                if (attempts >= 3) "DONE" else "PENDING"
            },
            shouldContinue = { it == "PENDING" }
        )
        assertEquals("DONE", result)
        assertTrue(attempts >= 3)
    }

    @Test
    fun boundedPoll_respectsMaxAttempts() = runBlocking {
        var attempts = 0
        boundedPoll(
            maxAttempts = 3,
            intervalMs = 10,
            maxDurationMs = 5000,
            action = {
                attempts++
                "PENDING"
            },
            shouldContinue = { true }
        )
        assertEquals(3, attempts)
    }

    @Test
    fun boundedPoll_respectsMaxDuration() = runBlocking {
        val startTime = System.currentTimeMillis()
        boundedPoll(
            maxAttempts = 100,
            intervalMs = 100,
            maxDurationMs = 200,
            action = { "PENDING" },
            shouldContinue = { true }
        )
        val elapsed = System.currentTimeMillis() - startTime
        assertTrue("Duration $elapsed should be >= 200ms", elapsed >= 200)
        assertTrue("Duration $elapsed should be < 1000ms", elapsed < 1000)
    }

    @Test
    fun boundedPoll_stopsImmediatelyOnSuccess() = runBlocking {
        var attempts = 0
        val result = boundedPoll(
            maxAttempts = 10,
            intervalMs = 100,
            maxDurationMs = 5000,
            action = {
                attempts++
                if (attempts == 1) "SUCCESS" else "PENDING"
            },
            shouldContinue = { it == "PENDING" }
        )
        assertEquals("SUCCESS", result)
        assertEquals(1, attempts)
    }

    @Test
    fun boundedPoll_stopsImmediatelyOnFailed() = runBlocking {
        var attempts = 0
        val result = boundedPoll(
            maxAttempts = 10,
            intervalMs = 100,
            maxDurationMs = 5000,
            action = {
                attempts++
                if (attempts == 1) "FAILED" else "PENDING"
            },
            shouldContinue = { it == "PENDING" }
        )
        assertEquals("FAILED", result)
        assertEquals(1, attempts)
    }

    @Test
    fun boundedPoll_continuesOnUnknown() = runBlocking {
        var attempts = 0
        val result = boundedPoll(
            maxAttempts = 3,
            intervalMs = 10,
            maxDurationMs = 5000,
            action = {
                attempts++
                "UNKNOWN"
            },
            shouldContinue = { it == "UNKNOWN" }
        )
        assertEquals("UNKNOWN", result)
        assertEquals(3, attempts)
    }
}