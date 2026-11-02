package io.github.keshavop.featureflags.server

import io.github.keshavop.featureflags.core.hooks.EvaluationHook
import io.github.keshavop.featureflags.core.log.FeatureFlagsLogger
import io.github.keshavop.featureflags.sync.DataSourceFactory
import io.github.keshavop.featureflags.sync.HttpTransport
import java.time.Duration

/**
 * Where the flag delivery service lives (HLD §7). The SDK key is sent in the `Authorization` header, never in URLs.
 * Stream and events default to [baseUri] when they are not served separately.
 */
public data class ServerEndpoints
    @JvmOverloads
    constructor(
        val baseUri: String,
        val streamUri: String = baseUri,
        val eventsUri: String = baseUri,
    ) {
        internal val snapshotUrl: String get() = baseUri.trimEnd('/') + "/sdk/v1/server/snapshot"
        internal val streamUrl: String get() = streamUri.trimEnd('/') + "/sdk/v1/server/stream"
        internal val eventsUrl: String get() = eventsUri.trimEnd('/') + "/sdk/v1/events/bulk"
    }

/** How the SDK receives flag data. */
public sealed interface ServerDataSource {
    /** Server-Sent Events with automatic polling fallback (default, ADR-003). */
    public data class Streaming
        @JvmOverloads
        constructor(
            val initialReconnectDelay: Duration = Duration.ofSeconds(1),
            val fallbackPollInterval: Duration = Duration.ofSeconds(30),
        ) : ServerDataSource

    /** Conditional polling only, for environments that block long-lived connections. */
    public data class Polling
        @JvmOverloads
        constructor(
            val pollInterval: Duration = Duration.ofSeconds(30),
        ) : ServerDataSource {
            init {
                require(pollInterval >= MIN_POLL_INTERVAL) { "pollInterval must be at least 30 seconds" }
            }
        }

    /** Any other source: [io.github.keshavop.featureflags.sync.TestDataSource], [FileDataSource], offline. */
    public data class Custom(
        val factory: DataSourceFactory,
    ) : ServerDataSource

    public companion object {
        internal val MIN_POLL_INTERVAL: Duration = Duration.ofSeconds(30)
    }
}

public data class ServerEventsConfig
    @JvmOverloads
    constructor(
        val enabled: Boolean = true,
        val capacity: Int = 10_000,
        val flushInterval: Duration = Duration.ofSeconds(5),
        /** Attributes never sent in events, for every context. */
        val privateAttributes: Set<String> = emptySet(),
        val allAttributesPrivate: Boolean = false,
    )

/**
 * Server SDK configuration (LLD §3.3). Kotlin callers use named arguments; Java callers use [builder].
 * Without [endpoints] the SDK needs a [ServerDataSource.Custom] source and sends no events.
 */
public data class ServerConfig(
    val endpoints: ServerEndpoints? = null,
    val dataSource: ServerDataSource = ServerDataSource.Streaming(),
    /** How long `create()` blocks waiting for the first flag data. */
    val startWait: Duration = Duration.ofSeconds(5),
    val events: ServerEventsConfig = ServerEventsConfig(),
    val hooks: List<EvaluationHook> = emptyList(),
    /** Defaults to SLF4J (logger `io.github.keshavop.featureflags`). */
    val logger: FeatureFlagsLogger? = null,
    /** Replaces the built-in OkHttp transport (proxies, custom TLS, tests). */
    val transport: HttpTransport? = null,
) {
    public class Builder {
        private var config = ServerConfig()

        public fun endpoints(value: ServerEndpoints): Builder = apply { config = config.copy(endpoints = value) }

        public fun dataSource(value: ServerDataSource): Builder = apply { config = config.copy(dataSource = value) }

        public fun startWait(value: Duration): Builder = apply { config = config.copy(startWait = value) }

        public fun events(value: ServerEventsConfig): Builder = apply { config = config.copy(events = value) }

        public fun hooks(value: List<EvaluationHook>): Builder = apply { config = config.copy(hooks = value) }

        public fun logger(value: FeatureFlagsLogger): Builder = apply { config = config.copy(logger = value) }

        public fun transport(value: HttpTransport): Builder = apply { config = config.copy(transport = value) }

        public fun build(): ServerConfig = config
    }

    public companion object {
        @JvmField
        public val DEFAULT: ServerConfig = ServerConfig()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
