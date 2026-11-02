package io.github.keshavop.featureflags.server

import io.github.keshavop.featureflags.server.internal.DefaultServerClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Entry point of the server SDK. */
public object FeatureFlagServer {
    /**
     * Creates a client and blocks up to [ServerConfig.startWait] for the first flag data. If the data does not arrive in
     * time the client is returned anyway, serves defaults and keeps connecting in the background (`isInitialized()` is
     * false until then).
     */
    @JvmStatic
    @JvmOverloads
    public fun create(
        sdkKey: String,
        config: ServerConfig = ServerConfig.DEFAULT,
    ): FeatureFlagServerClient {
        val client = DefaultServerClient(sdkKey, config)
        try {
            client.started.get(config.startWait.toMillis(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            client.logger.warn("No flag data within ${config.startWait}; serving defaults until it arrives")
        }
        return client
    }

    /** Creates a client without blocking; the future completes once the first flag data arrived or the source failed. */
    @JvmStatic
    @JvmOverloads
    public fun createAsync(
        sdkKey: String,
        config: ServerConfig = ServerConfig.DEFAULT,
    ): CompletableFuture<FeatureFlagServerClient> {
        val client = DefaultServerClient(sdkKey, config)
        return client.started.thenApply { client }
    }
}
