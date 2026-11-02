package io.github.keshavop.featureflags.core.log

import kotlin.concurrent.atomics.AtomicReference

/** Logging seam; platform modules adapt it to SLF4J, Logcat, os_log or the console. */
public interface FeatureFlagsLogger {
    public fun debug(message: String)

    public fun info(message: String)

    public fun warn(
        message: String,
        error: Throwable? = null,
    )

    public fun error(
        message: String,
        error: Throwable? = null,
    )
}

public object NoOpLogger : FeatureFlagsLogger {
    override fun debug(message: String): Unit = Unit

    override fun info(message: String): Unit = Unit

    override fun warn(
        message: String,
        error: Throwable?,
    ): Unit = Unit

    override fun error(
        message: String,
        error: Throwable?,
    ): Unit = Unit
}

/** Runs a log statement once per key, so a broken flag evaluated a million times logs once. Bounded and thread-safe. */
public class LogOnce(
    private val capacity: Int = 1_000,
) {
    private val seen = AtomicReference(emptySet<String>())

    public fun run(
        key: String,
        log: () -> Unit,
    ) {
        while (true) {
            val current = seen.load()
            if (key in current) return
            val next = if (current.size >= capacity) setOf(key) else current + key
            if (seen.compareAndSet(current, next)) break
        }
        log()
    }
}
