package io.github.keshavop.featureflags.events

import io.github.keshavop.featureflags.core.log.FeatureFlagsLogger
import io.github.keshavop.featureflags.core.log.NoOpLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Buffers, summarizes and sends analytics events (LLD §9). [record] never blocks or throws: when the buffer is full the
 * event is dropped and counted. A single consumer coroutine owns all mutable state.
 */
public class EventProcessor(
    private val config: EventsConfig,
    private val sender: EventSender,
    private val scope: CoroutineScope,
    private val logger: FeatureFlagsLogger = NoOpLogger,
) {
    private sealed interface Message {
        class Record(
            val event: InputEvent,
        ) : Message

        class Flush(
            val done: CompletableDeferred<Unit>?,
        ) : Message
    }

    private val inbox = Channel<Message>(config.capacity)
    private val dropped = AtomicInt(0)
    private val disabled = AtomicReference(false)
    private val redactor = ContextRedactor(config)
    private val summarizer = Summarizer()
    private val exposures = LruSet(config.dedupeCapacity)
    private val outbox = ArrayList<JsonElement>()

    /** Events dropped because the buffer was full. */
    public val droppedEvents: Int get() = dropped.load()

    public val isDisabled: Boolean get() = disabled.load()

    init {
        scope.launch {
            for (message in inbox) {
                when (message) {
                    is Message.Record -> process(message.event)
                    is Message.Flush -> {
                        sendBatch()
                        message.done?.complete(Unit)
                    }
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(config.flushIntervalMillis)
                inbox.trySend(Message.Flush(null))
            }
        }
    }

    public fun record(event: InputEvent) {
        if (disabled.load()) return
        if (inbox.trySend(Message.Record(event)).isFailure) dropped.fetchAndIncrement()
    }

    /** Sends everything recorded so far; returns once the batch has been handed to the sender. */
    public suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        inbox.send(Message.Flush(done))
        done.await()
    }

    /** Flushes and stops accepting events. */
    public suspend fun close() {
        flush()
        inbox.close()
    }

    private suspend fun process(event: InputEvent) {
        when (event) {
            is InputEvent.Evaluation -> {
                summarizer.add(event)
                if (event.exposure && exposures.add("${event.context.fullyQualifiedKey}|${event.flagKey}|${event.variation}")) {
                    outbox += exposureJson(event)
                }
            }
            is InputEvent.Custom -> outbox += customJson(event)
            is InputEvent.Identify -> outbox += identifyJson(event)
        }
        if (outbox.size >= config.batchSize) sendBatch()
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun sendBatch() {
        val summary = summarizer.drain()
        if (outbox.isEmpty() && summary == null) return
        val events = ArrayList(outbox).also { list -> summary?.let(list::add) }
        outbox.clear()
        if (disabled.load()) return
        val payload = JsonArray(events).toString()
        val payloadId = Uuid.random().toString()
        var result = sender.send(payload, payloadId)
        if (result == SendResult.RETRY) {
            delay(RETRY_DELAY_MILLIS)
            result = sender.send(payload, payloadId)
        }
        when (result) {
            SendResult.SENT -> Unit
            SendResult.RETRY, SendResult.DROP -> logger.warn("Dropped a batch of ${events.size} events (send failed: $result)")
            SendResult.DISABLE -> {
                logger.error("Events endpoint rejected the SDK key; event sending is disabled")
                disabled.store(true)
            }
        }
    }

    private fun exposureJson(event: InputEvent.Evaluation): JsonObject =
        JsonObject(
            buildMap {
                put("kind", JsonPrimitive("exposure"))
                put("creationDate", JsonPrimitive(event.creationDate))
                put("key", JsonPrimitive(event.flagKey))
                event.variation?.let { put("variation", JsonPrimitive(it)) }
                event.version?.let { put("version", JsonPrimitive(it)) }
                put("value", event.value)
                event.reason?.let { put("reason", JsonPrimitive(it.kind)) }
                put("context", redactor.redact(event.context))
            },
        )

    private fun customJson(event: InputEvent.Custom): JsonObject =
        JsonObject(
            buildMap {
                put("kind", JsonPrimitive("custom"))
                put("creationDate", JsonPrimitive(event.creationDate))
                put("key", JsonPrimitive(event.key))
                event.data?.let { put("data", it) }
                event.metricValue?.let { put("metricValue", JsonPrimitive(it)) }
                put("contextKeys", JsonObject(event.context.kinds.mapValues { JsonPrimitive(it.value.key) }))
            },
        )

    private fun identifyJson(event: InputEvent.Identify): JsonObject =
        JsonObject(
            mapOf(
                "kind" to JsonPrimitive("identify"),
                "creationDate" to JsonPrimitive(event.creationDate),
                "context" to redactor.redact(event.context),
            ),
        )

    private companion object {
        const val RETRY_DELAY_MILLIS = 1_000L
    }
}
