package io.github.keshavop.featureflags.client

import io.github.keshavop.featureflags.client.internal.DefaultFeatureFlagClient
import io.github.keshavop.featureflags.core.context.EvaluationContext

/** Builds a client; platform modules (Android, iOS, Web) wrap this with their own platform pieces. */
public object FeatureFlagClients {
    public fun create(
        clientKey: String,
        context: EvaluationContext,
        config: ClientConfig,
        platform: ClientPlatform,
    ): FeatureFlagClient = DefaultFeatureFlagClient(clientKey, context, config, platform)
}
