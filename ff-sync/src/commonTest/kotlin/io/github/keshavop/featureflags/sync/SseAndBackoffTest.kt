package io.github.keshavop.featureflags.sync

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SseAndBackoffTest {
    @Test
    fun parsesEventsCommentsAndMultilineData() {
        // the last event id carries over to later events, as in the SSE spec
        val parser = SseParser()
        val lines = listOf(": heartbeat", "", "event: put", "data: {\"a\":", "data: 1}", "id: 7", "", "data:no-space", "")
        val events = lines.mapNotNull(parser::feed)
        assertEquals(listOf(SseEvent("put", "{\"a\":\n1}", "7"), SseEvent("message", "no-space", "7")), events)
    }

    @Test
    fun blankLineWithoutDataIsNotAnEvent() {
        val parser = SseParser()
        parser.feed("event: put")
        assertNull(parser.feed(""))
    }

    @Test
    fun backoffGrowsToCapAndResetsAfterHealthyConnection() {
        val backoff = Backoff(base = 1.seconds, cap = 30.seconds, resetAfter = 60.seconds, random = Random(1))
        val delays = List(10) { backoff.nextDelay() }
        assertTrue(delays.all { it <= 30.seconds })
        assertTrue(delays.take(1).all { it <= 1.seconds })
        backoff.connectionEnded(healthyFor = 10.seconds)
        assertEquals(10, backoff.attempts)
        backoff.connectionEnded(healthyFor = 61.seconds)
        assertEquals(0, backoff.attempts)
    }
}
