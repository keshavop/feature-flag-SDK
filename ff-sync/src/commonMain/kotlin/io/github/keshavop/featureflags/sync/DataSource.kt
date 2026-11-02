package io.github.keshavop.featureflags.sync

import kotlinx.coroutines.flow.StateFlow

/** Keeps a flag store up to date from somewhere (streaming, polling, a file, a test). */
public interface DataSource {
    /** Starts in the background. Completes with true once the first valid payload is applied, false on permanent failure. */
    public suspend fun start(): Boolean

    public fun stop()

    public val status: StateFlow<DataSourceStatus>
}

public data class DataSourceStatus(
    val state: State,
    val sinceMillis: Long,
    val lastError: ErrorInfo? = null,
) {
    public enum class State {
        /** Not yet received a valid payload. */
        INITIALIZING,

        /** Receiving updates. */
        VALID,

        /** Was valid or initializing, now retrying; flags are served from the last good data. */
        INTERRUPTED,

        /** Stopped for good (closed, or a permanent error such as an invalid SDK key). */
        OFF,
    }
}

public data class ErrorInfo(
    val kind: Kind,
    val statusCode: Int? = null,
    val message: String? = null,
    val timeMillis: Long,
) {
    public enum class Kind { NETWORK, HTTP, INVALID_DATA, UNKNOWN }
}
