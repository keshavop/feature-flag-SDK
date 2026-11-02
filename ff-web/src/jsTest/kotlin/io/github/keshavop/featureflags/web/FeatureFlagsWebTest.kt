package io.github.keshavop.featureflags.web

import io.github.keshavop.featureflags.core.SdkInfo
import io.github.keshavop.featureflags.sync.HttpRequest
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs on Node 22, which has the same `fetch`, `Response` and `ReadableStream` as browsers. */
class FeatureFlagsWebTest {
    private val requests = mutableListOf<dynamic>()
    private val originalFetch: dynamic = js("globalThis.fetch")

    @AfterTest
    fun restoreFetch() {
        val original = originalFetch
        js("globalThis.fetch = original")
    }

    /** Replaces `fetch`; [respond] gets (url, init) and returns [status, body]. */
    private fun stubFetch(respond: (String, dynamic) -> Pair<Int, String>) {
        val handler: (String, dynamic) -> Promise<dynamic> = { url, init ->
            requests.add(init)
            val (status, body) = respond(url, init)
            val response: dynamic = if (status == 304) js("new Response(null, { status: 304 })") else newResponse(body, status)
            Promise.resolve(response)
        }
        js("globalThis.fetch = handler")
    }

    private fun newResponse(
        body: String,
        status: Int,
    ): dynamic = js("new Response(body, { status: status, headers: { 'ETag': '\"e1\"', 'Content-Type': 'application/json' } })")

    private fun evaluated(vararg flags: Pair<String, String>) =
        """{"schemaVersion":1,"flags":{${flags.joinToString(
            ",",
        ) { "\"${it.first}\":{\"value\":${it.second},\"variation\":1,\"version\":2}" }}}}"""

    private fun options(extra: String = ""): InitOptions =
        js("JSON.parse")("""{"baseUri":"https://flags.test","sendEvents":false,"timeoutMs":2000$extra}""").unsafeCast<InitOptions>()

    @Test
    fun exposesCoreVersion() {
        assertEquals(SdkInfo.version, sdkVersion())
    }

    @Test
    fun initializeFetchesAndServesPlainJsValues() =
        runTest {
            stubFetch { _, _ -> 200 to evaluated("dark-mode" to "true", "theme" to "\"blue\"", "limits" to """{"max":3}""") }
            val client = initialize("cli-key", js("({ kind: 'user', key: 'u-42', plan: 'pro' })"), options()).await()
            assertTrue(client.boolVariation("dark-mode", false))
            assertEquals("blue", client.stringVariation("theme", "red"))
            assertEquals(3, client.jsonVariation("limits", js("({})")).max as Int)
            assertEquals("fallback", client.stringVariation("missing", "fallback"))
            assertEquals("ERROR", client.variationDetail("missing", null).reason.kind as String)
            assertEquals("VALID", client.status)
            val init = requests.first()
            assertEquals("POST", init.method as String)
            assertEquals("cli-key", init.headers["Authorization"] as String)
            assertTrue((init.body as String).contains("\"key\":\"u-42\""))
            client.close()
        }

    @Test
    fun bootstrapIsServedWithoutNetwork() =
        runTest {
            stubFetch { _, _ -> error("no network in this test") }
            val bootstrap = """{"flags":{"ssr-flag":{"value":true,"variation":1,"version":1}}}"""
            val client = initialize("cli-key", js("({ key: 'u-1' })"), options(""","offline":true,"bootstrap":$bootstrap""")).await()
            assertTrue(client.boolVariation("ssr-flag", false))
            assertEquals(true, client.allFlags()["ssr-flag"] as Boolean)
            client.close()
        }

    @Test
    fun changeListenersAndIdentify() =
        runTest {
            stubFetch { _, init ->
                200 to
                    if ((init.body as String).contains("bob")) evaluated("who" to "\"bob\"") else evaluated("who" to "\"alice\"")
            }
            val client = initialize("cli-key", js("({ key: 'alice' })"), options()).await()
            val seen = mutableListOf<String>()
            val unsubscribe = client.on("change:who") { newValue: dynamic, _: dynamic -> seen += newValue as String }
            assertEquals("FRESH", client.identify(js("({ key: 'bob' })")).await())
            assertEquals("bob", client.stringVariation("who", ""))
            assertEquals(listOf("bob"), seen.filter { it == "bob" }.distinct())
            unsubscribe()
            client.close()
        }

    @Test
    fun streamedBodyIsSplitIntoLines() =
        runTest {
            val stream: dynamic =
                js(
                    """new Response(new ReadableStream({ start(c) { const e = new TextEncoder();
                    c.enqueue(e.encode('event: ping\ndata: {}\n')); c.enqueue(e.encode('\n: heartbeat\r\nlast')); c.close(); } }), { status: 200 })""",
                )
            val handler: (String, dynamic) -> Promise<dynamic> = { _, _ -> Promise.resolve(stream) }
            js("globalThis.fetch = handler")
            val response = FetchTransport().openStream(HttpRequest("GET", "https://flags.test/sdk/v1/client/stream"))
            assertEquals(200, response.status)
            assertEquals(listOf("event: ping", "data: {}", "", ": heartbeat"), response.lines.toList())
        }

    @Test
    fun unknownEventNameIsRejected() =
        runTest {
            stubFetch { _, _ -> 200 to evaluated() }
            val client = initialize("cli-key", js("({ key: 'u' })"), options()).await()
            val failure = runCatching { client.on("nope") { _: dynamic -> } }
            assertTrue(failure.isFailure)
            client.close()
        }
}
