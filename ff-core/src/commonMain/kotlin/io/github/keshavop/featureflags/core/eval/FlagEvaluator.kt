package io.github.keshavop.featureflags.core.eval

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.log.FeatureFlagsLogger
import io.github.keshavop.featureflags.core.log.LogOnce
import io.github.keshavop.featureflags.core.log.NoOpLogger
import io.github.keshavop.featureflags.core.model.Clause
import io.github.keshavop.featureflags.core.model.FlagConfig
import io.github.keshavop.featureflags.core.model.Operator
import io.github.keshavop.featureflags.core.model.Rollout
import io.github.keshavop.featureflags.core.model.RolloutKind
import io.github.keshavop.featureflags.core.model.Segment
import io.github.keshavop.featureflags.core.model.Snapshot
import io.github.keshavop.featureflags.core.model.VariationOrRollout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Evaluates flags against a [Snapshot] (LLD §4). Pure and thread-safe: it keeps no state besides log de-duplication.
 *
 * Order: missing → off → prerequisites → individual targets → rules (first match) → fallthrough.
 * A result with a null value means "serve the caller's default"; this never throws.
 */
public class FlagEvaluator(
    private val logger: FeatureFlagsLogger = NoOpLogger,
) {
    private val logOnce = LogOnce()

    public fun evaluate(
        snapshot: Snapshot,
        flagKey: String,
        context: EvaluationContext,
    ): EvaluationDetail<JsonElement?> =
        try {
            val flag = snapshot.flag(flagKey) ?: return errorDetail(ErrorKind.FLAG_NOT_FOUND)
            evaluateFlag(flag, context, snapshot, ArrayDeque())
        } catch (e: MalformedFlagException) {
            logOnce.run("malformed:$flagKey:${e.message}") { logger.error("Flag '$flagKey' is malformed: ${e.message}") }
            errorDetail(ErrorKind.MALFORMED_FLAG)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            logger.error("Unexpected error evaluating '$flagKey'", e)
            errorDetail(ErrorKind.EXCEPTION)
        }

    /** Evaluates every live flag, e.g. to bootstrap a client SDK from the server (`allFlagsState`). */
    public fun evaluateAll(
        snapshot: Snapshot,
        context: EvaluationContext,
    ): Map<String, EvaluationDetail<JsonElement?>> =
        snapshot.flags.values
            .filterNot { it.deleted }
            .associate { it.key to evaluate(snapshot, it.key, context) }

    private fun evaluateFlag(
        flag: FlagConfig,
        context: EvaluationContext,
        snapshot: Snapshot,
        visiting: ArrayDeque<String>,
    ): EvaluationDetail<JsonElement?> {
        if (!flag.on) return offDetail(flag, EvaluationReason.Off)

        if (flag.key in visiting) throw MalformedFlagException("prerequisite cycle through '${flag.key}'")
        visiting.addLast(flag.key)
        try {
            for (prerequisite in flag.prerequisites) {
                val prerequisiteFlag = snapshot.flag(prerequisite.key)
                val result = prerequisiteFlag?.let { evaluateFlag(it, context, snapshot, visiting) }
                if (prerequisiteFlag == null || !prerequisiteFlag.on || result?.variationIndex != prerequisite.variation) {
                    return offDetail(flag, EvaluationReason.PrerequisiteFailed(prerequisite.key))
                }
            }
        } finally {
            visiting.removeLast()
        }

        for (target in flag.targets) {
            val kind = context.kind(target.contextKind) ?: continue
            if (kind.key in target.values) return variationDetail(flag, target.variation, EvaluationReason.TargetMatch)
        }

        flag.rules.forEachIndexed { index, rule ->
            if (rule.clauses.all { clauseMatches(it, context, snapshot, depth = 0) }) {
                return resolve(flag, rule.variationOrRollout, context) { inExperiment ->
                    EvaluationReason.RuleMatch(index, rule.id, inExperiment)
                }
            }
        }

        return resolve(flag, flag.fallthrough, context) { inExperiment ->
            if (inExperiment) EvaluationReason.FALLTHROUGH_EXPERIMENT else EvaluationReason.FALLTHROUGH
        }
    }

    private inline fun resolve(
        flag: FlagConfig,
        outcome: VariationOrRollout,
        context: EvaluationContext,
        reason: (inExperiment: Boolean) -> EvaluationReason,
    ): EvaluationDetail<JsonElement?> {
        outcome.variation?.let { return variationDetail(flag, it, reason(false)) }
        val rollout = outcome.rollout ?: throw MalformedFlagException("rule has neither variation nor rollout")
        val (variation, inExperiment) = resolveRollout(flag, rollout, context)
        return variationDetail(flag, variation, reason(inExperiment))
    }

    private fun resolveRollout(
        flag: FlagConfig,
        rollout: Rollout,
        context: EvaluationContext,
    ): Pair<Int, Boolean> {
        validate(rollout)
        val bucketValue =
            context.kind(rollout.contextKind)?.bucketableValue(rollout.bucketBy)
                // not bucketable → control (last range), never treatment (ADR-006)
                ?: return rollout.weights.last().variation to false
        val bucket =
            if (rollout.seed != null) {
                Bucketer.bucket(rollout.seed.toString(), "", bucketValue)
            } else {
                Bucketer.bucket(flag.key, flag.salt, bucketValue)
            }
        return Bucketer.pick(rollout.weights, bucket) to (rollout.kind == RolloutKind.EXPERIMENT)
    }

    private fun validate(rollout: Rollout) {
        if (rollout.weights.isEmpty()) throw MalformedFlagException("rollout has no weights")
        if (rollout.weights.any { it.weight < 0 }) throw MalformedFlagException("rollout has a negative weight")
        val total = rollout.weights.sumOf { it.weight.toLong() }
        if (total > Bucketer.BUCKETS) throw MalformedFlagException("rollout weights sum to $total > ${Bucketer.BUCKETS}")
    }

    private fun clauseMatches(
        clause: Clause,
        context: EvaluationContext,
        snapshot: Snapshot,
        depth: Int,
    ): Boolean {
        if (clause.op == Operator.SEGMENT_MATCH) {
            val inAny =
                clause.values.any { value ->
                    value.isString && snapshot.segment(value.content)?.let { segmentMatches(it, context, snapshot, depth) } == true
                }
            return inAny != clause.negate
        }
        if (clause.op == Operator.UNKNOWN) {
            logOnce.run("unknown-op") { logger.warn("A clause uses an operator this SDK does not know; it never matches") }
            return false
        }
        val kind = context.kind(clause.contextKind) ?: return false
        val attribute = kind.resolve(clause.attribute) ?: return false
        val candidates: List<JsonPrimitive> =
            when (attribute) {
                is JsonArray -> attribute.filterIsInstance<JsonPrimitive>()
                is JsonPrimitive -> listOf(attribute)
                else -> return false
            }
        val matched =
            if (clause.op == Operator.NOT_IN) {
                // "not in [a, b]" holds when no value is in the list
                candidates.none { candidate -> clause.values.any { Operators.jsonEquals(candidate, it) } }
            } else {
                candidates.any { candidate -> clause.values.any { Operators.matches(clause.op, candidate, it, snapshot) } }
            }
        return matched != clause.negate
    }

    private fun segmentMatches(
        segment: Segment,
        context: EvaluationContext,
        snapshot: Snapshot,
        depth: Int,
    ): Boolean {
        if (depth >= MAX_SEGMENT_DEPTH) throw MalformedFlagException("segments nested deeper than $MAX_SEGMENT_DEPTH")
        val key = context.kind(segment.contextKind)?.key
        if (key != null) {
            if (key in segment.excluded) return false
            if (key in segment.included) return true
        }
        for (rule in segment.rules) {
            if (!rule.clauses.all { clauseMatches(it, context, snapshot, depth + 1) }) continue
            val weight = rule.weight ?: return true
            val value = context.kind(rule.contextKind)?.bucketableValue(rule.bucketBy) ?: continue
            if (Bucketer.bucket(segment.key, segment.salt, value) < weight) return true
        }
        return false
    }

    private fun variationDetail(
        flag: FlagConfig,
        index: Int,
        reason: EvaluationReason,
    ): EvaluationDetail<JsonElement?> {
        val value = flag.variations.getOrNull(index) ?: throw MalformedFlagException("variation $index does not exist")
        return EvaluationDetail(value, index, reason)
    }

    private fun offDetail(
        flag: FlagConfig,
        reason: EvaluationReason,
    ): EvaluationDetail<JsonElement?> {
        val off = flag.offVariation ?: return EvaluationDetail(null, null, reason)
        return variationDetail(flag, off, reason)
    }

    private fun errorDetail(kind: ErrorKind): EvaluationDetail<JsonElement?> = EvaluationDetail(null, null, EvaluationReason.error(kind))

    private class MalformedFlagException(
        message: String,
    ) : Exception(message)

    private companion object {
        const val MAX_SEGMENT_DEPTH = 10
    }
}
