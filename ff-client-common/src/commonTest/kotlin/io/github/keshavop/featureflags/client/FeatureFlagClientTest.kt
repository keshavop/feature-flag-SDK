package io.github.keshavop.featureflags.client

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.ErrorKind
import io.github.keshavop.featureflags.core.eval.EvaluationReason
import io.github.keshavop.featureflags.core.model.EvaluatedFlag
import io.github.keshavop.featureflags.core.model.EvaluatedSnapshot
import io.github.keshavop.featureflags.sync.DataSourceStatus
import io.github.keshavop.featureflags.sync.HttpResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureFlagClientTest {
    private val alice = EvaluationContext.user("alice").set("plan", "pro").build()
    private val bob = EvaluationContext.user("bob").build()
    private val transport = ScriptedTransport()
    private val storage = InMemoryKeyValueStore()
    private val lifecycle = MutableAppLifecycle()
    private val clients = mutableListOf<FeatureFlagClient>()

    private fun TestScope.client(
        context: EvaluationContext = alice,
        config: ClientConfig =
            ClientConfig(endpoints = ClientEndpoints("https://flags.test"), events = ClientEventsConfig(flushInterval = 60.minutes)),
    ): FeatureFlagClient {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val platform =
            ClientPlatform(
                name = "test",
                transport = transport,
                storage = storage,
                lifecycle = lifecycle,
                listenerDispatcher = dispatcher,
                backgroundDispatcher = dispatcher,
                clock = { testScheduler.currentTime },
            )
        return FeatureFlagClients.create("mob-key", context, config, platform).also { clients += it }
    }

    private fun TestScope.closeAll() {
        clients.forEach { it.close() }
        advanceUntilIdle()
    }

    @Test
    fun fetchesForContextAndServesValues() =
        runTest {
            transport.handler = { ok(evaluated("dark-mode" to true, "limit" to 5, "theme" to "blue"), etag = "\"e1\"") }
            val client = client()
            assertEquals(
                ErrorKind.CLIENT_NOT_READY,
                (client.boolVariationDetail("dark-mode", false).reason as EvaluationReason.Error).errorKind,
            )
            runCurrent()
            assertTrue(client.boolVariation("dark-mode", false))
            assertEquals(5, client.intVariation("limit", 0))
            assertEquals("blue", client.stringVariation("theme", ""))
            assertEquals("r1", (client.boolVariationDetail("dark-mode", false).reason as EvaluationReason.RuleMatch).ruleId)
            assertEquals(ErrorKind.WRONG_TYPE, (client.intVariationDetail("theme", 0).reason as EvaluationReason.Error).errorKind)
            assertEquals(ErrorKind.FLAG_NOT_FOUND, (client.boolVariationDetail("nope", false).reason as EvaluationReason.Error).errorKind)
            val request = transport.evaluations().single()
            assertEquals("mob-key", request.headers["Authorization"])
            assertTrue(request.body!!.contains("\"key\":\"alice\""), "context is POSTed, not put in the URL")
            assertEquals(DataSourceStatus.State.VALID, client.status.value.state)
            closeAll()
        }

    @Test
    fun coldStartServesCacheBeforeTheNetworkAnswers() =
        runTest {
            transport.handler = { ok(evaluated("banner" to "v1")) }
            client()
            runCurrent()
            closeAll()

            val slow = gate()
            transport.handler = {
                slow.await()
                ok(evaluated("banner" to "v2"))
            }
            val restarted = client()
            runCurrent()
            assertEquals("v1", restarted.stringVariation("banner", "default"), "cached value served while offline-ish")
            slow.complete(Unit)
            runCurrent()
            assertEquals("v2", restarted.stringVariation("banner", "default"))
            closeAll()
        }

    @Test
    fun pollsWithEtagAndNotifiesListeners() =
        runTest {
            var version = 1
            transport.handler = { request ->
                if (request.headers["If-None-Match"] == "\"e$version\"") {
                    HttpResponse(304, emptyMap(), "")
                } else {
                    ok(evaluated("flag" to version), etag = "\"e$version\"")
                }
            }
            val client = client()
            runCurrent()
            val changes = mutableListOf<FlagChange>()
            client.addListener("flag") { changes += it }
            advanceTimeBy(35.seconds)
            assertEquals(2, transport.evaluations().size)
            assertTrue(changes.isEmpty(), "304 must not notify")
            version = 2
            advanceTimeBy(35.seconds)
            assertEquals(listOf(FlagChange("flag", JsonPrimitive(1), JsonPrimitive(2))), changes)
            closeAll()
        }

    @Test
    fun responseForPreviousContextIsDiscarded() =
        runTest {
            val aliceGate = gate()
            transport.handler = { request ->
                if (request.body!!.contains("alice")) {
                    aliceGate.await()
                    ok(evaluated("who" to "alice"))
                } else {
                    // bob's own fetch fails, so a leaked response for alice would stay visible
                    throw IllegalStateException("network down")
                }
            }
            val client = client()
            runCurrent()
            val identify = async { client.identify(bob) }
            runCurrent()
            aliceGate.complete(Unit)
            assertEquals(IdentifyResult.NONE, identify.await())
            runCurrent()
            assertEquals("none", client.stringVariation("who", "none"), "alice's late response leaked into bob's context")
            assertEquals(bob, client.context)
            closeAll()
        }

    @Test
    fun identifyServesCachedValuesWhenOffline() =
        runTest {
            transport.handler = { request -> ok(evaluated("who" to if (request.body!!.contains("bob")) "bob" else "alice")) }
            val client = client()
            runCurrent()
            client.identify(bob)
            transport.handler = { throw IllegalStateException("offline") }
            assertEquals(IdentifyResult.CACHED, client.identify(alice))
            assertEquals("alice", client.stringVariation("who", "none"))
            closeAll()
        }

    @Test
    fun backgroundPausesPollingAndForegroundRefreshes() =
        runTest {
            transport.handler = { ok(evaluated("f" to true)) }
            client()
            runCurrent()
            lifecycle.setForeground(false)
            advanceTimeBy(10.minutes)
            assertEquals(1, transport.evaluations().size, "no polling in the background")
            lifecycle.setForeground(true)
            runCurrent()
            assertEquals(2, transport.evaluations().size, "stale data refreshed on foreground")
            closeAll()
        }

    @Test
    fun invalidKeyStopsRequests() =
        runTest {
            transport.handler = { HttpResponse(401, emptyMap(), "") }
            val client = client()
            advanceTimeBy(10.minutes)
            assertEquals(1, transport.evaluations().size)
            assertEquals(DataSourceStatus.State.OFF, client.status.value.state)
            assertFalse(client.refresh())
            closeAll()
        }

    @Test
    fun bootstrapAndOfflineModeNeedNoNetwork() =
        runTest {
            val bootstrap = EvaluatedSnapshot(flags = mapOf("ssr" to EvaluatedFlag(JsonPrimitive(true), variation = 1, version = 1)))
            val client =
                client(config = ClientConfig(endpoints = ClientEndpoints("https://flags.test"), offline = true, bootstrap = bootstrap))
            advanceTimeBy(5.minutes)
            assertTrue(client.boolVariation("ssr", false))
            assertTrue(transport.evaluations().isEmpty())
            closeAll()
        }

    @Test
    fun eventsAreFlushedWhenTheAppGoesToBackground() =
        runTest {
            transport.handler =
                { request -> if (request.url.contains("events")) HttpResponse(202, emptyMap(), "") else ok(evaluated("f" to true)) }
            val client = client()
            runCurrent()
            repeat(3) { client.boolVariation("f", false) }
            client.track("purchase", metricValue = 9.5)
            lifecycle.setForeground(false)
            runCurrent()
            val body = transport.events().single().body!!
            assertTrue(body.contains("\"kind\":\"identify\""))
            assertTrue(body.contains("\"kind\":\"custom\""))
            assertTrue(body.contains("\"kind\":\"summary\""))
            assertFalse(body.contains("\"plan\":\"pro\"") && body.contains("\"_meta\""), "sanity")
            closeAll()
        }

    @Test
    fun flagUpdatesFlowEmitsChanges() =
        runTest {
            transport.handler = { ok(evaluated("f" to true)) }
            val client = client()
            val first = async { client.flagUpdates.first() }
            runCurrent()
            assertEquals(FlagChange("f", null, JsonPrimitive(true)), first.await())
            closeAll()
        }
}
