package io.github.keshavop.featureflags.core.eval

import io.github.keshavop.featureflags.core.model.Rollout
import io.github.keshavop.featureflags.core.model.WeightedVariation
import kotlin.math.roundToInt

/** Deterministic percentage bucketing (ADR-004): `murmur3("<key>.<salt>.<value>") mod 100 000`. */
public object Bucketer {
    public const val BUCKETS: Int = 100_000

    /** Bucket in `[0, BUCKETS)` for [bucketByValue]; the same inputs give the same bucket on every platform. */
    public fun bucket(
        keyOrSeed: String,
        salt: String,
        bucketByValue: String,
    ): Int {
        val hash = Murmur3.hash32x86("$keyOrSeed.$salt.$bucketByValue").toUInt()
        return (hash % BUCKETS.toUInt()).toInt()
    }

    /** Variation whose range contains [bucket]; buckets past the last range (weights summing below 100 %) get the last one. */
    public fun pick(
        weights: List<WeightedVariation>,
        bucket: Int,
    ): Int {
        var cumulative = 0
        for (weighted in weights) {
            cumulative += weighted.weight
            if (bucket < cumulative) return weighted.variation
        }
        return weights.last().variation
    }
}

/** Converts human percentages to rollout weights (1 % = 1 000 buckets, 0.001 % granularity). */
public object Percent {
    public const val SCALE: Int = 1_000

    public fun toWeight(percent: Double): Int {
        require(percent in 0.0..100.0) { "percentage must be within 0..100, was $percent" }
        return (percent * SCALE).roundToInt()
    }
}

/**
 * The common two-way rollout: [percent] of contexts get [treatment], the rest get [control].
 * Treatment comes first so raising [percent] later never removes a context that already had it (ADR-006).
 */
public fun percentageRollout(
    percent: Double,
    treatment: Int,
    control: Int,
    bucketBy: String = "key",
    contextKind: String = "user",
): Rollout {
    val weight = Percent.toWeight(percent)
    return Rollout(
        bucketBy = bucketBy,
        contextKind = contextKind,
        weights = listOf(WeightedVariation(treatment, weight), WeightedVariation(control, Bucketer.BUCKETS - weight)),
    )
}
