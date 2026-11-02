package io.github.keshavop.featureflags.server.metrics

import io.github.keshavop.featureflags.core.eval.EvaluationDetail
import io.github.keshavop.featureflags.core.eval.EvaluationReason
import io.github.keshavop.featureflags.core.hooks.EvaluationHook
import io.github.keshavop.featureflags.core.hooks.HookContext
import io.github.keshavop.featureflags.core.hooks.HookData
import io.github.keshavop.featureflags.server.FeatureFlagServerClient
import io.github.keshavop.featureflags.sync.DataSourceStatus
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.serialization.json.JsonElement

/**
 * Micrometer metrics (HLD §11). Add [hook] to `ServerConfig.hooks` for `ff.eval.count{flag,reason}`, and call [bind]
 * with the client for `ff.datasource.state` (0 initializing, 1 valid, 2 interrupted, 3 off). Requires
 * `io.micrometer:micrometer-core` on the classpath.
 */
public class MicrometerMetrics(
    private val registry: MeterRegistry,
) {
    public val hook: EvaluationHook =
        object : EvaluationHook {
            override val name: String = "micrometer"

            override fun afterEvaluation(
                hookContext: HookContext,
                data: HookData,
                detail: EvaluationDetail<JsonElement?>,
            ): HookData {
                val reason = detail.reason
                val reasonTag = if (reason is EvaluationReason.Error) "ERROR_${reason.errorKind}" else reason.kind
                registry.counter("ff.eval.count", "flag", hookContext.flagKey, "reason", reasonTag).increment()
                return data
            }
        }

    public fun bind(client: FeatureFlagServerClient) {
        Gauge
            .builder("ff.datasource.state") {
                client.dataSourceStatus.status.state.ordinal
                    .toDouble()
            }.description("Flag data source state: " + DataSourceStatus.State.entries.joinToString { "${it.ordinal}=${it.name}" })
            .register(registry)
    }
}
