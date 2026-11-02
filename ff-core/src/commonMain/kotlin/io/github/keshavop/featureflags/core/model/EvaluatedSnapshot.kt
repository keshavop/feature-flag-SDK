package io.github.keshavop.featureflags.core.model

import io.github.keshavop.featureflags.core.eval.ErrorKind
import io.github.keshavop.featureflags.core.eval.EvaluationReason
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Flag values already evaluated for one context (LLD §5.2). Client SDKs receive this instead of rules, so targeting
 * data never reaches devices (ADR-002); the server SDK produces it for SSR bootstrap (`allFlagsState`).
 */
@Serializable
public data class EvaluatedSnapshot(
    val schemaVersion: Int = Snapshot.SCHEMA_VERSION,
    val contextHash: String? = null,
    val flags: Map<String, EvaluatedFlag> = emptyMap(),
)

@Serializable
public data class EvaluatedFlag(
    val value: JsonElement,
    val variation: Int? = null,
    val version: Long = 0,
    val reason: SerializedReason? = null,
    val trackEvents: Boolean = false,
)

/** Wire form of [EvaluationReason]. */
@Serializable
public data class SerializedReason(
    val kind: String,
    val ruleIndex: Int? = null,
    val ruleId: String? = null,
    val inExperiment: Boolean? = null,
    val prerequisiteKey: String? = null,
    val errorKind: String? = null,
) {
    /** Back to an [EvaluationReason]; unknown kinds from a newer service read as `FALLTHROUGH`. */
    public fun toReason(): EvaluationReason =
        when (kind) {
            "OFF" -> EvaluationReason.Off
            "TARGET_MATCH" -> EvaluationReason.TargetMatch
            "RULE_MATCH" -> EvaluationReason.RuleMatch(ruleIndex ?: -1, ruleId.orEmpty(), inExperiment == true)
            "PREREQUISITE_FAILED" -> EvaluationReason.PrerequisiteFailed(prerequisiteKey.orEmpty())
            "ERROR" ->
                EvaluationReason.error(ErrorKind.entries.firstOrNull { it.name == errorKind } ?: ErrorKind.EXCEPTION)
            else -> EvaluationReason.Fallthrough(inExperiment == true)
        }

    public companion object {
        public fun of(reason: EvaluationReason): SerializedReason =
            when (reason) {
                is EvaluationReason.RuleMatch ->
                    SerializedReason(
                        reason.kind,
                        ruleIndex = reason.ruleIndex,
                        ruleId = reason.ruleId,
                        inExperiment = reason.inExperiment.takeIf { it },
                    )
                is EvaluationReason.Fallthrough -> SerializedReason(reason.kind, inExperiment = reason.inExperiment.takeIf { it })
                is EvaluationReason.PrerequisiteFailed -> SerializedReason(reason.kind, prerequisiteKey = reason.prerequisiteKey)
                is EvaluationReason.Error -> SerializedReason(reason.kind, errorKind = reason.errorKind.name)
                EvaluationReason.Off, EvaluationReason.TargetMatch -> SerializedReason(reason.kind)
            }
    }
}
