package io.github.keshavop.featureflags.events

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Aggregates evaluations into counters per flag × variation × version, so the SDK sends one summary instead of one
 * event per evaluation (HLD §12). Not thread-safe: owned by the processor's single consumer.
 */
internal class Summarizer {
    private data class CounterKey(
        val variation: Int?,
        val version: Long?,
        val value: JsonElement,
    )

    private class FlagSummary(
        val default: JsonElement,
    ) {
        val contextKinds = LinkedHashSet<String>()
        val counters = LinkedHashMap<CounterKey, Long>()
    }

    private var startDate = 0L
    private var endDate = 0L
    private val flags = LinkedHashMap<String, FlagSummary>()

    val isEmpty: Boolean get() = flags.isEmpty()

    fun add(event: InputEvent.Evaluation) {
        if (flags.isEmpty() || event.creationDate < startDate) startDate = event.creationDate
        if (event.creationDate > endDate) endDate = event.creationDate
        val summary = flags.getOrPut(event.flagKey) { FlagSummary(event.default) }
        summary.contextKinds += event.context.kinds.keys
        val key = CounterKey(event.variation, event.version, event.value)
        summary.counters[key] = (summary.counters[key] ?: 0) + 1
    }

    /** The summary event, then resets. */
    fun drain(): JsonObject? {
        if (flags.isEmpty()) return null
        val features =
            flags.mapValues { (_, summary) ->
                JsonObject(
                    mapOf(
                        "default" to summary.default,
                        "contextKinds" to JsonArray(summary.contextKinds.map(::JsonPrimitive)),
                        "counters" to
                            JsonArray(
                                summary.counters.map { (key, count) ->
                                    JsonObject(
                                        buildMap {
                                            key.variation?.let { put("variation", JsonPrimitive(it)) }
                                            key.version?.let { put("version", JsonPrimitive(it)) } ?: put("unknown", JsonPrimitive(true))
                                            put("value", key.value)
                                            put("count", JsonPrimitive(count))
                                        },
                                    )
                                },
                            ),
                    ),
                )
            }
        val event =
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("summary"),
                    "startDate" to JsonPrimitive(startDate),
                    "endDate" to JsonPrimitive(endDate),
                    "features" to JsonObject(features),
                ),
            )
        flags.clear()
        return event
    }
}

/** Bounded insertion-ordered set; the oldest entry is evicted when full. Not thread-safe. */
internal class LruSet(
    private val capacity: Int,
) {
    private val entries = LinkedHashSet<String>()

    /** True if [key] was not present. */
    fun add(key: String): Boolean {
        if (key in entries) return false
        if (entries.size >= capacity) entries.remove(entries.first())
        entries += key
        return true
    }

    fun clear() = entries.clear()
}
