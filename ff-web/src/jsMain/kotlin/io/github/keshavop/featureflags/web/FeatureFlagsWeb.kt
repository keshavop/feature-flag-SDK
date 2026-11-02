@file:OptIn(ExperimentalJsExport::class)

package io.github.keshavop.featureflags.web

import io.github.keshavop.featureflags.client.ClientConfig
import io.github.keshavop.featureflags.client.ClientEndpoints
import io.github.keshavop.featureflags.client.ClientEventsConfig
import io.github.keshavop.featureflags.client.ClientPlatform
import io.github.keshavop.featureflags.client.FeatureFlagClient
import io.github.keshavop.featureflags.client.FeatureFlagClients
import io.github.keshavop.featureflags.core.SdkInfo
import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.EvaluationReason
import io.github.keshavop.featureflags.core.model.EvaluatedSnapshot
import io.github.keshavop.featureflags.core.model.SerializedReason
import io.github.keshavop.featureflags.core.model.SnapshotCodec
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.js.Promise
import kotlin.time.Duration.Companion.milliseconds

/** Version of the SDK bundled in this npm package. */
@JsExport
public fun sdkVersion(): String = SdkInfo.version

/** Options for [initialize]. Every field is optional except `baseUri` when the SDK should fetch flags. */
@JsExport
public external interface InitOptions {
    public var baseUri: String?
    public var streamUri: String?
    public var eventsUri: String?

    /** Refresh interval while the page is visible, in milliseconds (minimum 30 000). */
    public var pollIntervalMs: Int?

    /** Listen for change pings for near-real-time updates. */
    public var streaming: Boolean?

    /** Server-rendered flags (the server SDK's `allFlagsState`), served before anything else: no flicker. */
    public var bootstrap: dynamic

    /** How long the returned promise waits for fresh values before resolving with cached/bootstrap values. */
    public var timeoutMs: Int?
    public var sendEvents: Boolean?
    public var privateAttributes: Array<String>?
    public var offline: Boolean?
    public var debug: Boolean?
}

/**
 * Creates a client for one end user (`context` in the wire format, e.g. `{ kind: "user", key: "u-42", plan: "pro" }`).
 * Resolves once fresh values arrived, or after `timeoutMs` (default 5 s) with cached or bootstrap values.
 */
@JsExport
@OptIn(DelicateCoroutinesApi::class)
public fun initialize(
    clientKey: String,
    context: dynamic,
    options: InitOptions? = null,
): Promise<WebClient> {
    val opts: InitOptions = options ?: js("({})").unsafeCast<InitOptions>()
    val platform =
        ClientPlatform(
            name = "web",
            transport = FetchTransport(),
            storage = LocalStorageStore(),
            lifecycle = BrowserLifecycle(),
            connectivity = BrowserConnectivity(),
            logger = ConsoleLogger(opts.debug == true),
        )
    val config =
        ClientConfig(
            endpoints = opts.baseUri?.let { ClientEndpoints(it, opts.streamUri ?: it, opts.eventsUri ?: it) },
            pollInterval = (opts.pollIntervalMs ?: 30_000).milliseconds,
            streaming = opts.streaming == true,
            offline = opts.offline == true,
            bootstrap = parseBootstrap(opts.bootstrap),
            events =
                ClientEventsConfig(
                    enabled = opts.sendEvents != false,
                    privateAttributes =
                        opts.privateAttributes?.toSet() ?: emptySet(),
                ),
        )
    val client = FeatureFlagClients.create(clientKey, parseContext(context), config, platform)
    return GlobalScope.promise {
        client.waitForFreshValues((opts.timeoutMs ?: DEFAULT_TIMEOUT_MS).toLong())
        WebClient(client)
    }
}

/** Browser-facing client. Values are plain JavaScript values; evaluation is synchronous and local. */
@JsExport
@OptIn(DelicateCoroutinesApi::class)
public class WebClient internal constructor(
    private val client: FeatureFlagClient,
) {
    public fun boolVariation(
        key: String,
        defaultValue: Boolean,
    ): Boolean = client.boolVariation(key, defaultValue)

    public fun stringVariation(
        key: String,
        defaultValue: String,
    ): String = client.stringVariation(key, defaultValue)

    public fun numberVariation(
        key: String,
        defaultValue: Double,
    ): Double = client.doubleVariation(key, defaultValue)

    /** Any JSON value (objects, arrays, …). */
    public fun jsonVariation(
        key: String,
        defaultValue: dynamic,
    ): dynamic = toJs(client.jsonVariation(key, fromJs(defaultValue)))

    /** `{ value, variationIndex, reason: { kind, … } }`. */
    public fun variationDetail(
        key: String,
        defaultValue: dynamic,
    ): dynamic {
        val detail = client.jsonVariationDetail(key, fromJs(defaultValue))
        val result: dynamic = js("({})")
        result.value = toJs(detail.value)
        result.variationIndex = detail.variationIndex
        result.reason = reasonToJs(detail.reason)
        return result
    }

    /** Every flag value as one object: `{ "dark-mode": true, … }`. */
    public fun allFlags(): dynamic = toJs(JsonObject(client.allFlags()))

    /** Switches user; resolves with `FRESH`, `CACHED`, `NONE` or `SUPERSEDED`. */
    public fun identify(context: dynamic): Promise<String> = GlobalScope.promise { client.identify(parseContext(context)).name }

    /**
     * `on("change", (changes) => …)` for every update, or `on("change:<flag>", (newValue, oldValue) => …)` for one flag.
     * Returns a function that removes the listener.
     */
    public fun on(
        event: String,
        handler: dynamic,
    ): () -> Unit {
        val handle =
            when {
                event == "change" ->
                    client.addAllFlagsListener { changes ->
                        val list = changes.map { change -> changeToJs(change.key, change.oldValue, change.newValue) }.toTypedArray()
                        callSafely { handler(list) }
                    }
                event.startsWith("change:") ->
                    client.addListener(
                        event.removePrefix("change:"),
                    ) { change -> callSafely { handler(toJs(change.newValue), toJs(change.oldValue)) } }
                else -> throw IllegalArgumentException("unknown event '$event'; use 'change' or 'change:<flag>'")
            }
        return { handle.remove() }
    }

    public fun track(
        eventName: String,
        data: dynamic = undefined,
        metricValue: Double? = null,
    ) {
        client.track(eventName, if (data == undefined) null else fromJs(data), metricValue)
    }

    public fun setOffline(offline: Boolean) {
        client.setOffline(offline)
    }

    public fun refresh(): Promise<Boolean> = GlobalScope.promise { client.refresh() }

    public fun flush(): Promise<Unit> = GlobalScope.promise { client.flush() }

    /** `INITIALIZING`, `VALID`, `INTERRUPTED` or `OFF`. */
    public val status: String get() = client.status.value.state.name

    public fun close() {
        client.close()
    }
}

private const val DEFAULT_TIMEOUT_MS = 5_000

/** A throwing application handler must not stop other listeners. */
private inline fun callSafely(block: () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        console.error("[feature-flags] change listener threw", e)
    }
}

private fun parseBootstrap(bootstrap: dynamic): EvaluatedSnapshot? {
    if (bootstrap == null || bootstrap == undefined) return null
    return SnapshotCodec.json.decodeFromString(EvaluatedSnapshot.serializer(), JSON.stringify(bootstrap))
}

private fun parseContext(context: dynamic): EvaluationContext =
    EvaluationContext.fromJson(SnapshotCodec.json.parseToJsonElement(JSON.stringify(context)).jsonObject)

private fun fromJs(value: dynamic): JsonElement =
    if (value == undefined) JsonNull else SnapshotCodec.json.parseToJsonElement(JSON.stringify(value))

private fun toJs(value: JsonElement?): dynamic = if (value == null) undefined else JSON.parse(value.toString())

private fun reasonToJs(reason: EvaluationReason): dynamic =
    JSON.parse(SnapshotCodec.json.encodeToString(SerializedReason.serializer(), SerializedReason.of(reason)))

private fun changeToJs(
    key: String,
    old: JsonElement?,
    new: JsonElement?,
): dynamic {
    val change: dynamic = js("({})")
    change.key = key
    change.oldValue = toJs(old)
    change.newValue = toJs(new)
    return change
}
