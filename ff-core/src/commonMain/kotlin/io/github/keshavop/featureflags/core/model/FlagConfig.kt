package io.github.keshavop.featureflags.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** One feature flag (LLD §2). Variations are referenced by index everywhere else. */
@Serializable
public data class FlagConfig(
    val key: String,
    val version: Long,
    val on: Boolean = false,
    val variations: List<JsonElement> = emptyList(),
    val offVariation: Int? = null,
    val prerequisites: List<Prerequisite> = emptyList(),
    val targets: List<Target> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val fallthrough: VariationOrRollout = VariationOrRollout(),
    val salt: String = "",
    val trackEvents: Boolean = false,
    /** Tombstone: kept so an out-of-order older update cannot resurrect the flag. */
    val deleted: Boolean = false,
)

/** [key] must evaluate to [variation] for the dependent flag to continue past its prerequisites. */
@Serializable
public data class Prerequisite(
    val key: String,
    val variation: Int,
)

/** Individual targeting: contexts of [contextKind] whose key is in [values] get [variation]. */
@Serializable
public data class Target(
    val contextKind: String = ContextKinds.USER,
    val values: Set<String> = emptySet(),
    val variation: Int,
)

/** A targeting rule: all [clauses] must match (AND). */
@Serializable
public data class Rule(
    val id: String,
    val clauses: List<Clause> = emptyList(),
    val variationOrRollout: VariationOrRollout = VariationOrRollout(),
)

/** One condition. Any of [values] may match (OR); [negate] inverts the result. */
@Serializable
public data class Clause(
    val contextKind: String = ContextKinds.USER,
    /** Attribute name, or a `/`-separated path into nested objects such as `/address/city`. */
    val attribute: String,
    val op: Operator,
    val values: List<JsonPrimitive> = emptyList(),
    val negate: Boolean = false,
)

/** Exactly one of [variation] (fixed) or [rollout] (percentage split) is set. */
@Serializable
public data class VariationOrRollout(
    val variation: Int? = null,
    val rollout: Rollout? = null,
)

@Serializable
public enum class RolloutKind { ROLLOUT, EXPERIMENT }

/**
 * Percentage split over [BUCKETS][io.github.keshavop.featureflags.core.eval.Bucketer.BUCKETS] buckets (ADR-006).
 * [weights] are ordered ranges; the rolled-out variation comes first and control last, so raising its weight only
 * ever adds contexts.
 */
@Serializable
public data class Rollout(
    val kind: RolloutKind = RolloutKind.ROLLOUT,
    val bucketBy: String = "key",
    val contextKind: String = ContextKinds.USER,
    val weights: List<WeightedVariation>,
    /** Experiments: bucket on this seed instead of flag key + salt, so assignment is independent of the flag key. */
    val seed: Int? = null,
)

@Serializable
public data class WeightedVariation(
    val variation: Int,
    /** In units of 0.001 %: 1 000 = 1 %, 100 000 = 100 %. */
    val weight: Int,
)

/** Well-known context kinds. Any other kind name is allowed too. */
public object ContextKinds {
    public const val USER: String = "user"
    public const val MULTI: String = "multi"
}
