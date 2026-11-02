package io.github.keshavop.featureflags.events

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.EvaluationReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EventProcessorTest {
    private class RecordingSender(
        vararg results: SendResult,
    ) : EventSender {
        val results = ArrayDeque(results.toList())
        val payloads = mutableListOf<Pair<String, String>>()

        override suspend fun send(
            payload: String,
            payloadId: String,
        ): SendResult {
            payloads += payload to payloadId
            return results.removeFirstOrNull() ?: SendResult.SENT
        }

        fun events(index: Int = payloads.lastIndex) = Json.parseToJsonElement(payloads[index].first).jsonArray.map { it.jsonObject }
    }

    private val user =
        EvaluationContext
            .user("u-42")
            .set("email", "a@b.c")
            .set("plan", "pro")
            .private("email")
            .build()

    private fun evaluation(
        flag: String = "f",
        variation: Int = 1,
        exposure: Boolean = false,
        context: EvaluationContext = user,
    ) = InputEvent.Evaluation(
        1_000,
        context,
        flag,
        JsonPrimitive(variation == 1),
        JsonPrimitive(false),
        variation,
        7,
        EvaluationReason.Fallthrough(),
        exposure,
    )

    @Test
    fun summarizesEvaluationsAndSendsCustomEvents() =
        runTest {
            val sender = RecordingSender()
            val processor = EventProcessor(EventsConfig(), sender, backgroundScope)
            repeat(3) { processor.record(evaluation()) }
            processor.record(evaluation(variation = 0))
            processor.record(InputEvent.Custom(2_000, user, "checkout", metricValue = 49.99))
            processor.flush()
            val events = sender.events()
            assertEquals(listOf("custom", "summary"), events.map { it["kind"]!!.jsonPrimitive.content })
            val counters =
                events[1]["features"]!!
                    .jsonObject["f"]!!
                    .jsonObject["counters"]!!
                    .jsonArray
                    .map { it.jsonObject }
            assertEquals(
                mapOf(1 to 3L, 0 to 1L),
                counters.associate {
                    it["variation"]!!.jsonPrimitive.content.toInt() to
                        it["count"]!!.jsonPrimitive.content.toLong()
                },
            )
            assertEquals("u-42", events[0]["contextKeys"]!!.jsonObject["user"]!!.jsonPrimitive.content)
        }

    @Test
    fun exposuresAreDeduplicatedAndRedacted() =
        runTest {
            val sender = RecordingSender()
            val processor = EventProcessor(EventsConfig(privateAttributes = setOf("plan")), sender, backgroundScope)
            repeat(5) { processor.record(evaluation(exposure = true)) }
            processor.flush()
            val exposures = sender.events().filter { it["kind"]!!.jsonPrimitive.content == "exposure" }
            assertEquals(1, exposures.size)
            val context = exposures.single()["context"] as JsonObject
            assertNull(context["email"])
            assertNull(context["plan"])
            assertEquals(
                listOf("email", "plan"),
                context["_meta"]!!.jsonObject["redactedAttributes"]!!.jsonArray.map { it.jsonPrimitive.content },
            )
        }

    @Test
    fun nestedPrivatePathsAreRemoved() {
        val context =
            EvaluationContext
                .user(
                    "u",
                ).set("address", Json.parseToJsonElement("""{"city":"Pune","street":"MG Road"}"""))
                .private("/address/street")
                .build()
        val redacted = ContextRedactor(EventsConfig()).redact(context)
        assertEquals("""{"city":"Pune"}""", redacted["address"].toString())
    }

    @Test
    fun retriesOnceWithSamePayloadIdThenDrops() =
        runTest {
            val sender = RecordingSender(SendResult.RETRY, SendResult.RETRY)
            val processor = EventProcessor(EventsConfig(), sender, backgroundScope)
            processor.record(evaluation())
            processor.flush()
            assertEquals(2, sender.payloads.size)
            assertEquals(sender.payloads[0].second, sender.payloads[1].second)
            assertFalse(processor.isDisabled)
        }

    @Test
    fun invalidKeyDisablesEvents() =
        runTest {
            val sender = RecordingSender(SendResult.DISABLE)
            val processor = EventProcessor(EventsConfig(), sender, backgroundScope)
            processor.record(evaluation())
            processor.flush()
            processor.record(evaluation())
            processor.flush()
            assertTrue(processor.isDisabled)
            assertEquals(1, sender.payloads.size)
        }

    @Test
    fun fullBufferDropsInsteadOfBlocking() =
        runTest {
            val processor = EventProcessor(EventsConfig(capacity = 10), RecordingSender(), backgroundScope)
            // nothing consumes before the test yields, so everything past the capacity is dropped
            repeat(25) { processor.record(evaluation()) }
            assertEquals(15, processor.droppedEvents)
        }

    @Test
    fun periodicFlush() =
        runTest {
            val sender = RecordingSender()
            val processor = EventProcessor(EventsConfig(flushIntervalMillis = 5_000), sender, backgroundScope)
            processor.record(evaluation())
            testScheduler.advanceTimeBy(5_001)
            testScheduler.runCurrent()
            assertEquals(1, sender.payloads.size)
        }
}
