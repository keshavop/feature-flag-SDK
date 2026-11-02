package io.github.keshavop.featureflags.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared status bookkeeping: INITIALIZING until the first valid payload, then VALID/INTERRUPTED, OFF for good. */
internal class StatusHolder(
    private val clock: EpochClock,
) {
    private val state = MutableStateFlow(DataSourceStatus(DataSourceStatus.State.INITIALIZING, clock.nowMillis()))
    val firstResult = CompletableDeferred<Boolean>()
    val status: StateFlow<DataSourceStatus> = state.asStateFlow()

    fun valid() {
        set(DataSourceStatus.State.VALID, state.value.lastError)
        firstResult.complete(true)
    }

    fun error(
        kind: ErrorInfo.Kind,
        statusCode: Int? = null,
        message: String? = null,
    ) {
        val error = ErrorInfo(kind, statusCode, message, clock.nowMillis())
        val current = state.value.state
        // stay INITIALIZING until data arrives, so callers can tell "never had data" from "lost the connection"
        val next = if (current == DataSourceStatus.State.INITIALIZING) current else DataSourceStatus.State.INTERRUPTED
        if (current != DataSourceStatus.State.OFF) set(next, error)
    }

    fun off(error: ErrorInfo? = state.value.lastError) {
        set(DataSourceStatus.State.OFF, error)
        firstResult.complete(false)
    }

    fun permanentHttpError(statusCode: Int) = off(ErrorInfo(ErrorInfo.Kind.HTTP, statusCode, "HTTP $statusCode", clock.nowMillis()))

    private fun set(
        newState: DataSourceStatus.State,
        error: ErrorInfo?,
    ) {
        val current = state.value
        if (current.state != newState || current.lastError != error) {
            state.value = DataSourceStatus(newState, if (current.state == newState) current.sinceMillis else clock.nowMillis(), error)
        }
    }
}
