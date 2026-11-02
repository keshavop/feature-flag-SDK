package io.github.keshavop.featureflags.events

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.EvaluationReason
import kotlinx.serialization.json.JsonElement

/** What SDKs record; the processor turns these into summaries and output events (LLD §5.3, §9). */
public sealed interface InputEvent {
    public val creationDate: Long
    public val context: EvaluationContext

    /** One flag evaluation. */
    public data class Evaluation(
        override val creationDate: Long,
        override val context: EvaluationContext,
        val flagKey: String,
        val value: JsonElement,
        val default: JsonElement,
        val variation: Int?,
        val version: Long?,
        val reason: EvaluationReason?,
        /** Emit an individual exposure event (flag has `trackEvents` or the context is in an experiment). */
        val exposure: Boolean,
    ) : InputEvent

    /** An application event, e.g. `checkout-completed` with a revenue [metricValue]. */
    public data class Custom(
        override val creationDate: Long,
        override val context: EvaluationContext,
        val key: String,
        val data: JsonElement? = null,
        val metricValue: Double? = null,
    ) : InputEvent

    /** Client SDKs: the current context changed. */
    public data class Identify(
        override val creationDate: Long,
        override val context: EvaluationContext,
    ) : InputEvent
}

public data class EventsConfig(
    /** Events buffered before new ones are dropped (counted in [EventProcessor.droppedEvents]). */
    val capacity: Int = 10_000,
    val flushIntervalMillis: Long = 5_000,
    /** Flush early once this many output events are waiting. */
    val batchSize: Int = 500,
    /** Exposure events are sent once per context, flag and variation within this many remembered keys. */
    val dedupeCapacity: Int = 10_000,
    /** Attributes stripped from every context in events, on top of each context's own private attributes. */
    val privateAttributes: Set<String> = emptySet(),
    val allAttributesPrivate: Boolean = false,
)
