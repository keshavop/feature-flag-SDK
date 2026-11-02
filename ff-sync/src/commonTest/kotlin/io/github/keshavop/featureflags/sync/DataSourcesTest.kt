package io.github.keshavop.featureflags.sync

import io.github.keshavop.featureflags.core.store.InMemoryFlagStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DataSourcesTest {
    private val pollUrl = "https://sdk.test/sdk/v1/server/snapshot"
    private val streamUrl = "https://sdk.test/sdk/v1/server/stream"
    private val transport = FakeTransport()
    private val store = InMemoryFlagStore()
    private val updater = StoreUpdater(store)

    private fun TestScope.polling(scope: CoroutineScope = backgroundScope) =
        PollingDataSource(
            transport,
            HttpRequest("GET", pollUrl),
            30.seconds,
            updater::applyPut,
            scope,
            clock = { testScheduler.currentTime },
        )

    private fun TestScope.streaming(withFallback: Boolean = true) =
        StreamingDataSource(
            transport,
            HttpRequest("GET", streamUrl),
            updater::apply,
            backgroundScope,
            fallback = if (withFallback) polling() else null,
            timeSource = testScheduler.timeSource,
            clock = { testScheduler.currentTime },
        )

    @Test
    fun pollingAppliesSnapshotAndSendsEtag() =
        runTest {
            transport.respond(pollUrl, 200, snapshotJson(flagJson("a", 1)), mapOf("ETag" to "\"e1\""))
            transport.respond(pollUrl, 304)
            val source = polling()
            assertTrue(source.start())
            assertNotNull(store.snapshot().flag("a"))
            advanceTimeBy(40.seconds)
            assertEquals("\"e1\"", transport.requests.last().headers["If-None-Match"])
            assertEquals(DataSourceStatus.State.VALID, source.status.value.state)
        }

    @Test
    fun pollingStopsOnInvalidKey() =
        runTest {
            transport.respond(pollUrl, 401)
            val source = polling()
            assertFalse(source.start())
            advanceTimeBy(5.minutes)
            assertEquals(1, transport.requests.size)
            assertEquals(DataSourceStatus.State.OFF, source.status.value.state)
            assertEquals(
                401,
                source.status.value.lastError
                    ?.statusCode,
            )
        }

    @Test
    fun pollingKeepsLastGoodDataOnErrors() =
        runTest {
            transport.respond(pollUrl, 200, snapshotJson(flagJson("a", 1)))
            transport.respond(pollUrl, 200, "{not json")
            transport.fail(pollUrl)
            val source = polling()
            source.start()
            advanceTimeBy(70.seconds)
            assertNotNull(store.snapshot().flag("a"))
            assertEquals(DataSourceStatus.State.INTERRUPTED, source.status.value.state)
        }

    @Test
    fun streamingAppliesPutPatchAndDelete() =
        runTest {
            val channel = transport.openStream()
            val source = streaming()
            val started = async { source.start() }
            sse("put", snapshotJson(flagJson("a", 1), flagJson("b", 1))).forEach { channel.send(it) }
            assertTrue(started.await())
            sse("patch", """{"path":"/flags/a","data":${flagJson("a", 2, on = false)}}""").forEach { channel.send(it) }
            sse("delete", """{"path":"/flags/b","version":2}""").forEach { channel.send(it) }
            runCurrent()
            assertFalse(store.snapshot().flag("a")!!.on)
            assertNull(store.snapshot().flag("b"))
            assertEquals(DataSourceStatus.State.VALID, source.status.value.state)
        }

    @Test
    fun streamingReconnectsAfterDisconnect() =
        runTest {
            transport.stream(lines = sse("put", snapshotJson(flagJson("a", 1))))
            transport.stream(lines = sse("put", snapshotJson(flagJson("a", 5))))
            val source = streaming()
            assertTrue(source.start())
            advanceTimeBy(5.seconds)
            assertEquals(5, store.snapshot().flag("a")!!.version)
            assertTrue(transport.requests.count { it.url == streamUrl } >= 2)
        }

    @Test
    fun malformedEventRestartsTheStream() =
        runTest {
            transport.stream(lines = sse("put", snapshotJson(flagJson("a", 1))) + sse("patch", "{garbage"))
            transport.stream(lines = sse("put", snapshotJson(flagJson("a", 2))))
            val source = streaming()
            source.start()
            advanceTimeBy(5.seconds)
            assertEquals(2, store.snapshot().flag("a")!!.version)
        }

    @Test
    fun fallsBackToPollingAfterThreeFailedConnections() =
        runTest {
            repeat(3) { transport.failStream() }
            transport.respond(pollUrl, 200, snapshotJson(flagJson("a", 9)))
            val source = streaming()
            val started = async { source.start() }
            advanceTimeBy(2.minutes)
            assertTrue(started.await())
            assertEquals(9, store.snapshot().flag("a")!!.version)
            assertTrue(transport.requests.any { it.url == pollUrl })
        }

    @Test
    fun streamingStopsOnInvalidKey() =
        runTest {
            transport.stream(status = 403)
            val source = streaming()
            assertFalse(source.start())
            advanceTimeBy(5.minutes)
            assertEquals(1, transport.requests.size)
            assertEquals(DataSourceStatus.State.OFF, source.status.value.state)
        }

    @Test
    fun testDataSourcePushesUpdatesToStores() =
        runTest {
            val td = TestDataSource()
            td.update(td.flag("x").variationForAll(true))
            val source = td.create(DataSourceContext(store, backgroundScope, null, io.github.keshavop.featureflags.core.log.NoOpLogger))
            source.start()
            assertEquals(
                1,
                store
                    .snapshot()
                    .flag("x")!!
                    .fallthrough.variation,
            )
            td.update(td.flag("x").fallthroughPercentage(30.0))
            assertEquals(
                30_000,
                store
                    .snapshot()
                    .flag("x")!!
                    .fallthrough.rollout!!
                    .weights
                    .first()
                    .weight,
            )
        }
}
