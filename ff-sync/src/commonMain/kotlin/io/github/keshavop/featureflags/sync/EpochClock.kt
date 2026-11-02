package io.github.keshavop.featureflags.sync

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Wall-clock source, replaceable in tests. */
public fun interface EpochClock {
    public fun nowMillis(): Long

    public companion object {
        @OptIn(ExperimentalTime::class)
        public val SYSTEM: EpochClock = EpochClock { Clock.System.now().toEpochMilliseconds() }
    }
}
