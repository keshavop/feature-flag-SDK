package io.github.keshavop.featureflags.client

import io.github.keshavop.featureflags.core.hooks.EvaluationHook
import io.github.keshavop.featureflags.core.model.EvaluatedSnapshot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Client delivery endpoints (HLD §7). The client key goes in the `Authorization` header; contexts are POSTed, never put
 * in URLs.
 */
public data class ClientEndpoints(
    val baseUri: String,
    val streamUri: String = baseUri,
    val eventsUri: String = baseUri,
) {
    internal val evaluateUrl: String get() = baseUri.trimEnd('/') + "/sdk/v1/client/evaluate?withReasons=true"
    internal val streamUrl: String get() = streamUri.trimEnd('/') + "/sdk/v1/client/stream"
    internal val eventsUrl: String get() = eventsUri.trimEnd('/') + "/sdk/v1/events/bulk"
}

public data class ClientEventsConfig(
    val enabled: Boolean = true,
    val capacity: Int = 500,
    val flushInterval: Duration = 30.seconds,
    val privateAttributes: Set<String> = emptySet(),
    val allAttributesPrivate: Boolean = false,
)

/** Client SDK configuration shared by Android, iOS and Web (LLD §3.3). */
public data class ClientConfig(
    /** Without endpoints the client only serves [bootstrap] or cached values. */
    val endpoints: ClientEndpoints? = null,
    /** How often to refresh while the app is in the foreground and online. Polling pauses in the background. */
    val pollInterval: Duration = 30.seconds,
    /** Also listen for change pings over Server-Sent Events, for near-real-time updates (HLD §6.3). */
    val streaming: Boolean = false,
    /** Start without network access; switch at runtime with `setOffline`. */
    val offline: Boolean = false,
    /** Values to serve before anything else is available, e.g. the server SDK's `allFlagsState` for SSR. */
    val bootstrap: EvaluatedSnapshot? = null,
    val persistence: Boolean = true,
    val maxCachedContexts: Int = 5,
    val events: ClientEventsConfig = ClientEventsConfig(),
    val hooks: List<EvaluationHook> = emptyList(),
) {
    init {
        require(pollInterval >= MIN_POLL_INTERVAL) { "pollInterval must be at least $MIN_POLL_INTERVAL" }
        require(maxCachedContexts >= 1) { "maxCachedContexts must be at least 1" }
    }

    public companion object {
        public val MIN_POLL_INTERVAL: Duration = 30.seconds
    }
}
