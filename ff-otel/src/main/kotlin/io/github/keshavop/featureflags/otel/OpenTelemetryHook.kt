package io.github.keshavop.featureflags.otel

import io.github.keshavop.featureflags.core.SdkInfo
import io.github.keshavop.featureflags.core.eval.EvaluationDetail
import io.github.keshavop.featureflags.core.eval.EvaluationReason
import io.github.keshavop.featureflags.core.hooks.EvaluationHook
import io.github.keshavop.featureflags.core.hooks.HookContext
import io.github.keshavop.featureflags.core.hooks.HookData
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.JsonElement

/**
 * Records every flag evaluation as a `feature_flag.evaluation` event on the current span, following the OpenTelemetry
 * semantic conventions for feature flags. Register it in `ServerConfig.hooks` (or a client config's hooks).
 *
 * The evaluated value and the context key can identify users, so both are off by default.
 */
public class OpenTelemetryHook
    @JvmOverloads
    constructor(
        private val includeValue: Boolean = false,
        private val includeContextKey: Boolean = false,
    ) : EvaluationHook {
        override val name: String = "opentelemetry"

        override fun afterEvaluation(
            hookContext: HookContext,
            data: HookData,
            detail: EvaluationDetail<JsonElement?>,
        ): HookData {
            val span = Span.current()
            if (!span.spanContext.isValid) return data
            val attributes =
                Attributes
                    .builder()
                    .put(KEY, hookContext.flagKey)
                    .put(PROVIDER, SdkInfo.NAME)
                    .put(REASON, reasonOf(detail.reason))
            detail.variationIndex?.let { attributes.put(VARIANT, it.toString()) }
            if (includeValue) detail.value?.let { attributes.put(VALUE, it.toString()) }
            if (includeContextKey) attributes.put(CONTEXT_ID, hookContext.context.fullyQualifiedKey)
            val reason = detail.reason
            if (reason is EvaluationReason.Error) attributes.put(ERROR_TYPE, reason.errorKind.name.lowercase())
            span.addEvent(EVENT_NAME, attributes.build())
            return data
        }

        private fun reasonOf(reason: EvaluationReason): String =
            when (reason) {
                EvaluationReason.Off -> "disabled"
                EvaluationReason.TargetMatch -> "targeting_match"
                is EvaluationReason.RuleMatch -> if (reason.inExperiment) "split" else "targeting_match"
                is EvaluationReason.Fallthrough -> if (reason.inExperiment) "split" else "default"
                is EvaluationReason.PrerequisiteFailed -> "disabled"
                is EvaluationReason.Error -> "error"
            }

        public companion object {
            public const val EVENT_NAME: String = "feature_flag.evaluation"
            public val KEY: AttributeKey<String> = AttributeKey.stringKey("feature_flag.key")
            public val PROVIDER: AttributeKey<String> = AttributeKey.stringKey("feature_flag.provider.name")
            public val VARIANT: AttributeKey<String> = AttributeKey.stringKey("feature_flag.result.variant")
            public val REASON: AttributeKey<String> = AttributeKey.stringKey("feature_flag.result.reason")
            public val VALUE: AttributeKey<String> = AttributeKey.stringKey("feature_flag.result.value")
            public val CONTEXT_ID: AttributeKey<String> = AttributeKey.stringKey("feature_flag.context.id")
            public val ERROR_TYPE: AttributeKey<String> = AttributeKey.stringKey("error.type")
        }
    }
