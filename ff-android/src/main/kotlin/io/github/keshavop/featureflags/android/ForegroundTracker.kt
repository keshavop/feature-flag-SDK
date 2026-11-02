package io.github.keshavop.featureflags.android

/**
 * Decides foreground/background from activity start/stop counts. A short grace period after the last stop avoids
 * flapping during configuration changes (rotation stops and restarts the activity). Pure logic, unit tested on the JVM.
 */
internal class ForegroundTracker(
    private val schedule: (delayMillis: Long, action: () -> Unit) -> Unit,
    private val onChange: (Boolean) -> Unit,
) {
    private var started = 0
    private var stopToken = 0
    private var foreground = false

    fun activityStarted() {
        started++
        stopToken++
        set(true)
    }

    fun activityStopped() {
        started = (started - 1).coerceAtLeast(0)
        if (started > 0) return
        val token = ++stopToken
        schedule(GRACE_MILLIS) { if (token == stopToken && started == 0) set(false) }
    }

    private fun set(value: Boolean) {
        if (value != foreground) {
            foreground = value
            onChange(value)
        }
    }

    companion object {
        const val GRACE_MILLIS = 700L
    }
}
