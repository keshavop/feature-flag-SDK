package io.github.keshavop.featureflags.server

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.ErrorKind
import io.github.keshavop.featureflags.core.eval.EvaluationReason
import io.github.keshavop.featureflags.sync.DataSourceStatus
import io.github.keshavop.featureflags.sync.TestDataSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.GzipSource
import okio.buffer
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerClientTest {
    private val server = MockWebServer()
    private val user =
        EvaluationContext
            .user("u-42")
            .set("email", "a@b.c")
            .set("country", "IN")
            .private("email")
            .build()

    @AfterTest
    fun tearDown() = server.shutdown()

    private fun flag(
        key: String,
        version: Int,
        on: Boolean = true,
        trackEvents: Boolean = false,
    ) = """{"key":"$key","version":$version,"on":$on,"trackEvents":$trackEvents,"variations":[false,true],"offVariation":0,
        "rules":[{"id":"india","clauses":[{"attribute":"country","op":"IN","values":["IN"]}],"variationOrRollout":{"variation":1}}],
        "fallthrough":{"variation":0}}""".replace("\n", "")

    private fun snapshot(vararg flags: Pair<String, String>) =
        """{"schemaVersion":1,"version":1,"flags":{${flags.joinToString(",") { "\"${it.first}\":${it.second}" }}}}"""

    private fun sse(vararg events: Pair<String, String>) = events.joinToString("") { "event: ${it.first}\ndata: ${it.second}\n\n" }

    private fun config(dataSource: ServerDataSource = ServerDataSource.Streaming(initialReconnectDelay = Duration.ofMillis(50))) =
        ServerConfig(
            endpoints = ServerEndpoints(server.url("/").toString()),
            dataSource = dataSource,
            startWait = Duration.ofSeconds(5),
            events = ServerEventsConfig(flushInterval = Duration.ofHours(1)),
        )

    private fun RecordedRequest.bodyText(): String {
        val raw = body
        return if (getHeader("Content-Encoding") == "gzip") GzipSource(raw).buffer().readUtf8() else raw.readUtf8()
    }

    @Test
    fun streamingInitializesAndAppliesPatches() {
        val stream = MockResponse().setHeader("Content-Type", "text/event-stream")
        val streamCount = AtomicInteger()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/sdk/v1/server/stream" ->
                            if (streamCount.incrementAndGet() == 1) {
                                // keep the first connection open for a while with throttled heartbeats, then end it
                                MockResponse()
                                    .setHeader("Content-Type", "text/event-stream")
                                    .setBody(sse("put" to snapshot("checkout" to flag("checkout", 1))) + ": heartbeat\n".repeat(40))
                                    .throttleBody(64, 100, TimeUnit.MILLISECONDS)
                            } else {
                                stream.setBody(
                                    sse(
                                        "put" to snapshot("checkout" to flag("checkout", 1)),
                                        "patch" to """{"path":"/flags/checkout","data":${flag("checkout", 2, on = false)}}""",
                                    ),
                                )
                            }
                        else -> MockResponse().setResponseCode(202)
                    }
            }
        FeatureFlagServer.create("srv-key", config()).use { client ->
            assertTrue(client.isInitialized())
            assertEquals(DataSourceStatus.State.VALID, client.dataSourceStatus.status.state)
            val first = client.boolVariationDetail("checkout", user, false)
            assertEquals(true, first.value)
            assertEquals("india", (first.reason as EvaluationReason.RuleMatch).ruleId)
            waitUntil { !client.boolVariation("checkout", user, true) }
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("srv-key", request.getHeader("Authorization"))
            assertTrue(request.getHeader("User-Agent")!!.startsWith("feature-flag-sdk/"))
        }
    }

    @Test
    fun fallsBackToPollingWhenStreamingIsBlocked() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/sdk/v1/server/stream" -> MockResponse().setResponseCode(502)
                        "/sdk/v1/server/snapshot" ->
                            MockResponse().setHeader("ETag", "\"v1\"").setBody(
                                snapshot(
                                    "checkout" to flag("checkout", 3),
                                ),
                            )
                        else -> MockResponse().setResponseCode(202)
                    }
            }
        FeatureFlagServer.create("srv-key", config()).use { client ->
            assertTrue(client.isInitialized())
            assertTrue(client.boolVariation("checkout", user, false))
        }
    }

    @Test
    fun invalidKeyStopsWithoutRetrying() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(401)
            }
        FeatureFlagServer.create("bad-key", config()).use { client ->
            assertFalse(client.isInitialized())
            assertEquals(DataSourceStatus.State.OFF, client.dataSourceStatus.status.state)
            val detail = client.stringVariationDetail("anything", user, "fallback")
            assertEquals("fallback", detail.value)
            assertEquals(EvaluationReason.error(ErrorKind.CLIENT_NOT_READY), detail.reason)
            Thread.sleep(300)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun pollingSendsEtagAndEventsAreSummarizedAndRedacted() {
        val events = LinkedBlockingQueue<RecordedRequest>()
        val polls = CopyOnWriteArrayList<RecordedRequest>()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/sdk/v1/server/snapshot" -> {
                            polls += request
                            MockResponse().setHeader("ETag", "\"v1\"").setBody(snapshot("exp" to flag("exp", 4, trackEvents = true)))
                        }
                        "/sdk/v1/events/bulk" -> {
                            events += request
                            MockResponse().setResponseCode(202)
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
            }
        FeatureFlagServer.create("srv-key", config(ServerDataSource.Polling())).use { client ->
            repeat(3) { client.boolVariation("exp", user, false) }
            client.track("checkout-completed", user, null, 49.99)
            client.flush()
        }
        val request = assertNotNull(events.poll(5, TimeUnit.SECONDS))
        assertEquals("gzip", request.getHeader("Content-Encoding"))
        assertNotNull(request.getHeader("X-SDK-Payload-Id"))
        val payload = Json.parseToJsonElement(request.bodyText()).jsonArray.map { it.jsonObject }
        assertEquals(listOf("exposure", "custom", "summary"), payload.map { it["kind"]!!.jsonPrimitive.content })
        val exposureContext = payload[0]["context"]!!.jsonObject
        assertEquals(null, exposureContext["email"], "private attribute leaked")
        val count =
            payload[2]["features"]!!
                .jsonObject["exp"]!!
                .jsonObject["counters"]!!
                .jsonArray
                .single()
                .jsonObject["count"]
        assertEquals(JsonPrimitive(3), count)
        assertEquals(1, polls.size)
    }

    @Test
    fun testDataSourceListenersAndAllFlagsState() {
        val td = TestDataSource()
        td.update(td.flag("banner").variationForAll(false))
        FeatureFlagServer.create("test", ServerConfig(dataSource = ServerDataSource.Custom(td))).use { client ->
            assertFalse(client.boolVariation("banner", user, true))
            val changes = CountDownLatch(1)
            val values = CopyOnWriteArrayList<FlagValueChangeEvent>()
            client.flagTracker.addFlagValueChangeListener("banner", user) {
                values += it
                changes.countDown()
            }
            td.update(td.flag("banner").variationForUser("u-42", true))
            assertTrue(changes.await(5, TimeUnit.SECONDS))
            assertEquals(JsonPrimitive(true), values.single().newValue)

            td.update(td.flag("rollout").fallthroughPercentage(100.0))
            val state = client.allFlagsState(user, withReasons = true)
            assertEquals(setOf("banner", "rollout"), state.flags.keys)
            assertEquals(
                "TARGET_MATCH",
                state.flags
                    .getValue("banner")
                    .reason!!
                    .kind,
            )
            assertEquals(64, state.contextHash!!.length)
        }
    }

    @Test
    fun fileDataSourceLoadsSnapshot() {
        val file = Files.createTempFile("flags", ".json")
        Files.writeString(file, snapshot("from-file" to flag("from-file", 1)))
        FeatureFlagServer.create("local", ServerConfig(dataSource = ServerDataSource.Custom(FileDataSource.factory(file)))).use { client ->
            assertTrue(client.boolVariation("from-file", user, false))
        }
    }

    @Test
    fun hooksSeeEveryEvaluationAndFailingHooksAreIsolated() {
        val td = TestDataSource()
        td.update(td.flag("f").variationForAll(true))
        val seen = CopyOnWriteArrayList<String>()
        val hooks =
            listOf(
                object : io.github.keshavop.featureflags.core.hooks.EvaluationHook {
                    override val name = "broken"

                    override fun beforeEvaluation(
                        hookContext: io.github.keshavop.featureflags.core.hooks.HookContext,
                        data: io.github.keshavop.featureflags.core.hooks.HookData,
                    ): io.github.keshavop.featureflags.core.hooks.HookData = error("boom")
                },
                object : io.github.keshavop.featureflags.core.hooks.EvaluationHook {
                    override val name = "recording"

                    override fun afterEvaluation(
                        hookContext: io.github.keshavop.featureflags.core.hooks.HookContext,
                        data: io.github.keshavop.featureflags.core.hooks.HookData,
                        detail: io.github.keshavop.featureflags.core.eval.EvaluationDetail<kotlinx.serialization.json.JsonElement?>,
                    ): io.github.keshavop.featureflags.core.hooks.HookData = data.also { seen += hookContext.flagKey }
                },
            )
        FeatureFlagServer.create("test", ServerConfig(dataSource = ServerDataSource.Custom(td), hooks = hooks)).use { client ->
            assertTrue(client.boolVariation("f", user, false))
            assertEquals(listOf("f"), seen)
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met within 10 s" }
            Thread.sleep(20)
        }
    }

    @Suppress("unused")
    private fun Buffer.text() = readUtf8()
}
