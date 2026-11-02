package io.github.keshavop.featureflags.core.model

import kotlinx.serialization.Serializable

/**
 * The complete, immutable flag state of one environment as delivered to server SDKs (LLD §2).
 *
 * Every evaluation reads one snapshot, so prerequisites and segments are always seen at the same version.
 * Updates never mutate a snapshot; they produce a new one (see [withFlag], [withSegment]).
 */
@Serializable
public data class Snapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    val environment: String = "",
    val version: Long = 0,
    val flags: Map<String, FlagConfig> = emptyMap(),
    val segments: Map<String, Segment> = emptyMap(),
) {
    /** Regex patterns used by `MATCHES` clauses, compiled once per snapshot instead of once per evaluation. */
    internal val regexCache: Map<String, Regex?> by lazy { compileRegexes(this) }

    /** Live (non-deleted) flag, or null. */
    public fun flag(key: String): FlagConfig? = flags[key]?.takeUnless { it.deleted }

    /** Live (non-deleted) segment, or null. */
    public fun segment(key: String): Segment? = segments[key]?.takeUnless { it.deleted }

    public fun withFlag(flag: FlagConfig): Snapshot = copy(flags = flags + (flag.key to flag), version = version + 1)

    public fun withSegment(segment: Segment): Snapshot = copy(segments = segments + (segment.key to segment), version = version + 1)

    public companion object {
        /** Wire schema major version this SDK understands. */
        public const val SCHEMA_VERSION: Int = 1

        public val EMPTY: Snapshot = Snapshot()
    }
}

private fun compileRegexes(snapshot: Snapshot): Map<String, Regex?> {
    val clauses =
        snapshot.flags.values.flatMap { flag -> flag.rules.flatMap { it.clauses } } +
            snapshot.segments.values.flatMap { segment -> segment.rules.flatMap { it.clauses } }
    return clauses
        .filter { it.op == Operator.MATCHES }
        .flatMap { clause -> clause.values.mapNotNull { if (it.isString) it.content else null } }
        .distinct()
        .associateWith { SafeRegex.compile(it) }
}

/**
 * Compiles the portable subset of regular expressions (RE2-like): constructs that behave differently on the
 * JVM, JavaScript and Kotlin/Native engines, or that can backtrack catastrophically, are rejected.
 */
internal object SafeRegex {
    private val unsupported = listOf("(?=", "(?!", "(?<=", "(?<!", "(?>", "\\p{", "\\P{")
    private val backReference = Regex("""\\[1-9]""")

    fun compile(pattern: String): Regex? {
        if (unsupported.any { it in pattern } || backReference.containsMatchIn(pattern)) return null
        return runCatching { Regex(pattern) }.getOrNull()
    }
}
