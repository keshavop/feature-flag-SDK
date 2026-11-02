package io.github.keshavop.featureflags.server

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.EvaluationDetail
import io.github.keshavop.featureflags.core.model.EvaluatedSnapshot
import io.github.keshavop.featureflags.sync.DataSourceStatus
import kotlinx.serialization.json.JsonElement
import java.io.Closeable
import java.time.Duration

/**
 * The server-side SDK client (LLD §3.1). Evaluation is local, lock-free and never throws: on any problem the caller's
 * default is returned and the detail's reason says why. One instance per process and SDK key; it is thread-safe.
 */
public interface FeatureFlagServerClient : Closeable {
    public fun boolVariation(
        key: String,
        context: EvaluationContext,
        default: Boolean,
    ): Boolean

    public fun stringVariation(
        key: String,
        context: EvaluationContext,
        default: String,
    ): String

    public fun intVariation(
        key: String,
        context: EvaluationContext,
        default: Int,
    ): Int

    public fun doubleVariation(
        key: String,
        context: EvaluationContext,
        default: Double,
    ): Double

    public fun jsonVariation(
        key: String,
        context: EvaluationContext,
        default: JsonElement,
    ): JsonElement

    public fun boolVariationDetail(
        key: String,
        context: EvaluationContext,
        default: Boolean,
    ): EvaluationDetail<Boolean>

    public fun stringVariationDetail(
        key: String,
        context: EvaluationContext,
        default: String,
    ): EvaluationDetail<String>

    public fun intVariationDetail(
        key: String,
        context: EvaluationContext,
        default: Int,
    ): EvaluationDetail<Int>

    public fun doubleVariationDetail(
        key: String,
        context: EvaluationContext,
        default: Double,
    ): EvaluationDetail<Double>

    public fun jsonVariationDetail(
        key: String,
        context: EvaluationContext,
        default: JsonElement,
    ): EvaluationDetail<JsonElement>

    /**
     * Every flag evaluated for [context], in the client snapshot format, e.g. to bootstrap the Web SDK during server-side
     * rendering. Records no analytics events.
     */
    public fun allFlagsState(
        context: EvaluationContext,
        withReasons: Boolean = false,
    ): EvaluatedSnapshot

    public fun track(
        eventName: String,
        context: EvaluationContext,
    ): Unit = track(eventName, context, null, null)

    public fun track(
        eventName: String,
        context: EvaluationContext,
        data: JsonElement?,
        metricValue: Double?,
    )

    /** True once flag data has been received. Before that, evaluations return defaults with `CLIENT_NOT_READY`. */
    public fun isInitialized(): Boolean

    public val dataSourceStatus: DataSourceStatusProvider

    public val flagTracker: FlagTracker

    /** Sends pending analytics events now (normally done every few seconds). */
    public fun flush()

    /** Flushes events and stops all background work. Idempotent. */
    override fun close()
}

public fun interface ListenerHandle : Closeable {
    override fun close()
}

public data class FlagChangeEvent(
    val key: String,
)

public fun interface FlagChangeListener {
    public fun onFlagChange(event: FlagChangeEvent)
}

public data class FlagValueChangeEvent(
    val key: String,
    val oldValue: JsonElement?,
    val newValue: JsonElement?,
)

public fun interface FlagValueChangeListener {
    public fun onFlagValueChange(event: FlagValueChangeEvent)
}

/** Notifies about flag configuration changes. Listeners run on a dedicated SDK thread, never on the data source. */
public interface FlagTracker {
    /** Fires for every flag whose configuration (or a prerequisite/segment it uses) changed. */
    public fun addFlagChangeListener(listener: FlagChangeListener): ListenerHandle

    /** Fires only when [flagKey]'s value for [context] actually changes. */
    public fun addFlagValueChangeListener(
        flagKey: String,
        context: EvaluationContext,
        listener: FlagValueChangeListener,
    ): ListenerHandle
}

public fun interface DataSourceStatusListener {
    public fun onStatusChange(status: DataSourceStatus)
}

public interface DataSourceStatusProvider {
    public val status: DataSourceStatus

    public fun addStatusListener(listener: DataSourceStatusListener): ListenerHandle

    /** Blocks until the data source reaches [state] (true) or [timeout] passes / it turns OFF (false). */
    public fun waitFor(
        state: DataSourceStatus.State,
        timeout: Duration,
    ): Boolean
}
