package io.github.keshavop.featureflags.core.model

import kotlinx.serialization.Serializable

/**
 * A reusable audience referenced by `SEGMENT_MATCH` clauses. Membership is decided in order:
 * [excluded] keys, [included] keys, then [rules].
 */
@Serializable
public data class Segment(
    val key: String,
    val version: Long,
    val contextKind: String = ContextKinds.USER,
    val included: Set<String> = emptySet(),
    val excluded: Set<String> = emptySet(),
    val rules: List<SegmentRule> = emptyList(),
    val salt: String = "",
    val deleted: Boolean = false,
)

/**
 * A segment rule. When [weight] is set only that share of matching contexts is included
 * (bucketed on segment key + salt), e.g. `weight = 30_000` keeps 30 %.
 */
@Serializable
public data class SegmentRule(
    val id: String,
    val clauses: List<Clause> = emptyList(),
    val weight: Int? = null,
    val bucketBy: String = "key",
    val contextKind: String = ContextKinds.USER,
)
