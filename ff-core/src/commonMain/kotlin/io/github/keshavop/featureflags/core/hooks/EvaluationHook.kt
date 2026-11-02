package io.github.keshavop.featureflags.core.hooks

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.EvaluationDetail
import io.github.keshavop.featureflags.core.log.FeatureFlagsLogger
import io.github.keshavop.featureflags.core.log.NoOpLogger
import kotlinx.serialization.json.JsonElement

/** What is being evaluated, passed to every hook stage. */
public data class HookContext(
    val flagKey: String,
    val context: EvaluationContext,
    val defaultValue: JsonElement,
)

/** Data a hook carries from [EvaluationHook.beforeEvaluation] to [EvaluationHook.afterEvaluation], e.g. a span. */
public typealias HookData = Map<String, Any?>

/**
 * Observes evaluations for logging, tracing or metrics (LLD §4.6). Hooks run synchronously in registration order and
 * cannot change the result; an exception thrown by a hook is logged and ignored.
 */
public interface EvaluationHook {
    public val name: String

    public fun beforeEvaluation(
        hookContext: HookContext,
        data: HookData,
    ): HookData = data

    public fun afterEvaluation(
        hookContext: HookContext,
        data: HookData,
        detail: EvaluationDetail<JsonElement?>,
    ): HookData = data
}

/** Runs [hooks] around an evaluation. With no hooks registered it calls the evaluation directly. */
public class HookRunner(
    private val hooks: List<EvaluationHook>,
    private val logger: FeatureFlagsLogger = NoOpLogger,
) {
    public fun run(
        hookContext: () -> HookContext,
        evaluate: () -> EvaluationDetail<JsonElement?>,
    ): EvaluationDetail<JsonElement?> {
        if (hooks.isEmpty()) return evaluate()
        val context = hookContext()
        val data = hooks.map { hook -> safely(hook, emptyMap()) { hook.beforeEvaluation(context, emptyMap()) } }
        val detail = evaluate()
        hooks.forEachIndexed { index, hook -> safely(hook, data[index]) { hook.afterEvaluation(context, data[index], detail) } }
        return detail
    }

    private inline fun safely(
        hook: EvaluationHook,
        fallback: HookData,
        stage: () -> HookData,
    ): HookData =
        try {
            stage()
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            logger.warn("Evaluation hook '${hook.name}' failed", e)
            fallback
        }
}
