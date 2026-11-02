package io.github.keshavop.featureflags.client

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.EvaluationDetail
import io.github.keshavop.featureflags.sync.DataSourceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * Client SDK for one end user (LLD §3.2). Evaluations are synchronous map lookups on an in-memory snapshot, safe to call
 * from the UI thread; they never block or throw. Network, disk and events run in the background.
 */
public interface FeatureFlagClient {
    public fun boolVariation(
        key: String,
        default: Boolean,
    ): Boolean

    public fun stringVariation(
        key: String,
        default: String,
    ): String

    public fun intVariation(
        key: String,
        default: Int,
    ): Int

    public fun doubleVariation(
        key: String,
        default: Double,
    ): Double

    public fun jsonVariation(
        key: String,
        default: JsonElement,
    ): JsonElement

    public fun boolVariationDetail(
        key: String,
        default: Boolean,
    ): EvaluationDetail<Boolean>

    public fun stringVariationDetail(
        key: String,
        default: String,
    ): EvaluationDetail<String>

    public fun intVariationDetail(
        key: String,
        default: Int,
    ): EvaluationDetail<Int>

    public fun doubleVariationDetail(
        key: String,
        default: Double,
    ): EvaluationDetail<Double>

    public fun jsonVariationDetail(
        key: String,
        default: JsonElement,
    ): EvaluationDetail<JsonElement>

    /** All current flag values for the current context. */
    public fun allFlags(): Map<String, JsonElement>

    public val context: EvaluationContext

    /**
     * Switches to [newContext] (log in/out). Cached values for that context are served at once; the result says whether
     * fresh values were fetched. Responses still in flight for the previous context are discarded.
     */
    public suspend fun identify(newContext: EvaluationContext): IdentifyResult

    /** Waits until values for the current context are fresh from the network (true) or [timeoutMillis] passes. */
    public suspend fun waitForFreshValues(timeoutMillis: Long): Boolean

    public fun track(
        eventName: String,
        data: JsonElement? = null,
        metricValue: Double? = null,
    )

    /** Called with old and new value whenever [key] changes. Runs on the platform's listener (main) thread. */
    public fun addListener(
        key: String,
        listener: FlagChangeListener,
    ): ListenerHandle

    /** Called once per update with every flag that changed. */
    public fun addAllFlagsListener(listener: AllFlagsListener): ListenerHandle

    /** Every change as a flow (Compose `collectAsState`, Swift `AsyncSequence`). */
    public val flagUpdates: Flow<FlagChange>

    public val status: StateFlow<DataSourceStatus>

    public fun setOffline(offline: Boolean)

    /** Fetches now instead of waiting for the next poll. */
    public suspend fun refresh(): Boolean

    public suspend fun flush()

    public fun close()
}

public data class FlagChange(
    val key: String,
    val oldValue: JsonElement?,
    val newValue: JsonElement?,
)

public fun interface FlagChangeListener {
    public fun onChange(change: FlagChange)
}

public fun interface AllFlagsListener {
    public fun onChange(changes: List<FlagChange>)
}

public fun interface ListenerHandle {
    public fun remove()
}

public enum class IdentifyResult {
    /** Fresh values fetched for the new context. */
    FRESH,

    /** Network unavailable or failed; serving cached values for the new context. */
    CACHED,

    /** No values available yet for the new context; defaults are served. */
    NONE,

    /** Another identify started before this one finished. */
    SUPERSEDED,
}
