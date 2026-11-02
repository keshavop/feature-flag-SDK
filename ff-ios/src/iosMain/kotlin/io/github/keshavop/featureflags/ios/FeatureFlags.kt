package io.github.keshavop.featureflags.ios

import io.github.keshavop.featureflags.client.ClientConfig
import io.github.keshavop.featureflags.client.ClientEndpoints
import io.github.keshavop.featureflags.client.ClientPlatform
import io.github.keshavop.featureflags.client.FeatureFlagClient
import io.github.keshavop.featureflags.client.FeatureFlagClients
import io.github.keshavop.featureflags.client.FlagChange
import io.github.keshavop.featureflags.client.ListenerHandle
import io.github.keshavop.featureflags.core.SdkInfo
import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.model.SnapshotCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.atomics.AtomicReference

/**
 * iOS entry point (LLD §10.2), shaped for Swift, where this object is `FeatureFlags.shared`. Call [start] once (on the
 * main thread, e.g. in your `App` init), then use [client]. Kotlin `suspend` functions such as `identify` appear in Swift
 * as `async`.
 *
 * ```swift
 * let flags = FeatureFlags.shared.start(mobileKey: "mob-…",
 *     context: FeatureFlags.shared.userContext(key: userId, attributes: ["plan": "pro"]),
 *     baseUri: "https://flags.example.com")
 * if flags.boolVariation(key: "new-checkout", default: false) { … }
 * let result = try await flags.identify(newContext: FeatureFlags.shared.userContext(key: newUserId, attributes: [:]))
 * ```
 */
public object FeatureFlags {
    private val instance = AtomicReference<FeatureFlagClient?>(null)

    /** The client created by [start]. */
    public val client: FeatureFlagClient
        get() = checkNotNull(instance.load()) { "FeatureFlags.start() has not been called" }

    public fun start(
        mobileKey: String,
        context: EvaluationContext,
        baseUri: String,
    ): FeatureFlagClient = start(mobileKey, context, ClientConfig(endpoints = ClientEndpoints(baseUri)))

    public fun start(
        mobileKey: String,
        context: EvaluationContext,
        config: ClientConfig,
    ): FeatureFlagClient {
        instance.load()?.let { return it }
        val platform =
            ClientPlatform(
                name = "ios",
                transport = UrlSessionTransport(SdkInfo.userAgent("ios")),
                storage = IosFileStore(),
                lifecycle = IosLifecycle(),
                connectivity = IosConnectivity(),
                listenerDispatcher = Dispatchers.Main,
                backgroundDispatcher = Dispatchers.Default,
                logger = NsLogLogger(),
            )
        val client = FeatureFlagClients.create(mobileKey, context, config, platform)
        return if (instance.compareAndSet(null, client)) client else instance.load()!!.also { client.close() }
    }

    /** Builds a user context from Swift values (`String`, `Bool`, `Int`, `Double`). */
    public fun userContext(
        key: String,
        attributes: Map<String, Any>,
    ): EvaluationContext = context("user", key, attributes)

    public fun context(
        kind: String,
        key: String,
        attributes: Map<String, Any>,
    ): EvaluationContext {
        val builder = EvaluationContext.builder(kind, key)
        attributes.forEach { (name, value) -> builder.set(name, toJson(value)) }
        return builder.build()
    }

    /** Calls [onChange] on the main thread whenever [key] changes; Swift closures work directly here. */
    public fun observe(
        client: FeatureFlagClient,
        key: String,
        onChange: (FlagChange) -> Unit,
    ): ListenerHandle = client.addListener(key) { onChange(it) }

    /** A JSON flag's value as JSON text, for Swift's `JSONDecoder`. */
    public fun jsonVariationText(
        client: FeatureFlagClient,
        key: String,
        defaultJson: String,
    ): String {
        val default = SnapshotCodec.json.parseToJsonElement(defaultJson)
        return client.jsonVariation(key, default).toString()
    }

    internal fun toJson(value: Any): JsonElement =
        when (value) {
            is JsonElement -> value
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
}
