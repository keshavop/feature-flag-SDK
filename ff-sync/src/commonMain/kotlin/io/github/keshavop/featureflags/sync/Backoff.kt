package io.github.keshavop.featureflags.sync

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Exponential backoff with full jitter (LLD §7.1): attempt n waits a random time in `[0, min(cap, base·2ⁿ)]`.
 * The attempt counter resets once a connection has been healthy for [resetAfter].
 */
public class Backoff(
    private val base: Duration = 1.seconds,
    private val cap: Duration = 30.seconds,
    private val resetAfter: Duration = 60.seconds,
    private val random: Random = Random.Default,
) {
    private var attempt = 0

    public val attempts: Int get() = attempt

    public fun nextDelay(): Duration {
        val ceiling = minOf(cap.inWholeMilliseconds, base.inWholeMilliseconds shl attempt.coerceAtMost(MAX_SHIFT))
        attempt++
        return random.nextLong(0, ceiling + 1).milliseconds
    }

    /** Call when a connection ends; resets the counter if it had been up for long enough. */
    public fun connectionEnded(healthyFor: Duration) {
        if (healthyFor >= resetAfter) attempt = 0
    }

    public fun reset() {
        attempt = 0
    }

    private companion object {
        const val MAX_SHIFT = 20
    }
}

/** Spreads periodic requests ±[fraction] so instances started together do not poll in lock step. */
public fun Duration.jittered(
    fraction: Double = 0.1,
    random: Random = Random.Default,
): Duration = this * (1 + random.nextDouble(-fraction, fraction))
