package io.github.keshavop.featureflags.android

import kotlin.test.Test
import kotlin.test.assertEquals

class ForegroundTrackerTest {
    private val pending = mutableListOf<() -> Unit>()
    private val states = mutableListOf<Boolean>()
    private val tracker = ForegroundTracker(schedule = { _, action -> pending += action }, onChange = { states += it })

    private fun runPending() {
        val actions = pending.toList()
        pending.clear()
        actions.forEach { it() }
    }

    @Test
    fun goesToBackgroundAfterGracePeriod() {
        tracker.activityStarted()
        tracker.activityStopped()
        assertEquals(listOf(true), states, "not background before the grace period")
        runPending()
        assertEquals(listOf(true, false), states)
    }

    @Test
    fun rotationDoesNotFlap() {
        tracker.activityStarted()
        tracker.activityStopped()
        tracker.activityStarted()
        runPending()
        assertEquals(listOf(true), states)
    }

    @Test
    fun multipleActivities() {
        tracker.activityStarted()
        tracker.activityStarted()
        tracker.activityStopped()
        runPending()
        assertEquals(listOf(true), states)
        tracker.activityStopped()
        runPending()
        assertEquals(listOf(true, false), states)
    }
}
