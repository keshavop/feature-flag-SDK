package io.github.keshavop.featureflags.otel

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.ErrorKind
import io.github.keshavop.featureflags.core.eval.EvaluationDetail
import io.github.keshavop.featureflags.core.eval.EvaluationReason
import io.github.keshavop.featureflags.core.hooks.HookContext
import io.github.keshavop.featureflags.core.hooks.HookRunner
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenTelemetryHookTest {
    private val exporter = InMemorySpanExporter.create()
    private val tracer =
        SdkTracerProvider
            .builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build()
            .get("test")
    private val user = EvaluationContext.user("u-42").build()

    private fun evaluateInSpan(
        hook: OpenTelemetryHook,
        detail: EvaluationDetail<kotlinx.serialization.json.JsonElement?>,
    ) {
        val span = tracer.spanBuilder("request").startSpan()
        span.makeCurrent().use {
            HookRunner(listOf(hook)).run({ HookContext("new-checkout", user, JsonPrimitive(false)) }) { detail }
        }
        span.end()
    }

    @Test
    fun recordsSemanticConventionEvent() {
        evaluateInSpan(
            OpenTelemetryHook(),
            EvaluationDetail(JsonPrimitive(true), 1, EvaluationReason.RuleMatch(0, "r", inExperiment = true)),
        )
        val event =
            exporter.finishedSpanItems
                .single()
                .events
                .single()
        assertEquals("feature_flag.evaluation", event.name)
        assertEquals("new-checkout", event.attributes.get(OpenTelemetryHook.KEY))
        assertEquals("1", event.attributes.get(OpenTelemetryHook.VARIANT))
        assertEquals("split", event.attributes.get(OpenTelemetryHook.REASON))
        assertNull(event.attributes.get(OpenTelemetryHook.VALUE), "value is opt-in")
        assertNull(event.attributes.get(OpenTelemetryHook.CONTEXT_ID), "context key is opt-in")
    }

    @Test
    fun errorsAndOptInAttributes() {
        evaluateInSpan(
            OpenTelemetryHook(includeValue = true, includeContextKey = true),
            EvaluationDetail(null, null, EvaluationReason.error(ErrorKind.FLAG_NOT_FOUND)),
        )
        val event =
            exporter.finishedSpanItems
                .single()
                .events
                .single()
        assertEquals("error", event.attributes.get(OpenTelemetryHook.REASON))
        assertEquals("flag_not_found", event.attributes.get(OpenTelemetryHook.ERROR_TYPE))
        assertEquals("user:u-42", event.attributes.get(OpenTelemetryHook.CONTEXT_ID))
    }

    @Test
    fun noSpanNoEvent() {
        HookRunner(listOf(OpenTelemetryHook())).run({ HookContext("f", user, JsonPrimitive(false)) }) {
            EvaluationDetail(JsonPrimitive(true), 1, EvaluationReason.TargetMatch)
        }
        assertTrue(exporter.finishedSpanItems.isEmpty())
    }
}
