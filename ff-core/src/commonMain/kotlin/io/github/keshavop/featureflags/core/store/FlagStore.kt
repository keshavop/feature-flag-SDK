package io.github.keshavop.featureflags.core.store

import io.github.keshavop.featureflags.core.model.FlagConfig
import io.github.keshavop.featureflags.core.model.Operator
import io.github.keshavop.featureflags.core.model.Segment
import io.github.keshavop.featureflags.core.model.Snapshot
import kotlin.concurrent.atomics.AtomicReference

public enum class ItemKind { FLAG, SEGMENT }

/** Flag keys whose evaluation result may have changed, including flags that depend on a changed flag or segment. */
public data class ChangeSet(
    val flagKeys: Set<String>,
) {
    val isEmpty: Boolean get() = flagKeys.isEmpty()
}

/**
 * Holds the current [Snapshot] (LLD §6, ADR-005). Reads are one atomic load and never block; writes replace the whole
 * snapshot copy-on-write. Every upsert/delete applies only when its version is newer than the stored one, so updates are
 * idempotent and may arrive out of order.
 */
public interface FlagStore {
    public fun snapshot(): Snapshot

    public val isInitialized: Boolean

    /** Replaces everything (an SSE `put` or a full poll). */
    public fun init(newSnapshot: Snapshot)

    public fun upsertFlag(flag: FlagConfig): Boolean

    public fun upsertSegment(segment: Segment): Boolean

    /** Stores a tombstone so a late, older upsert cannot resurrect the item. */
    public fun delete(
        kind: ItemKind,
        key: String,
        version: Long,
    ): Boolean
}

public class InMemoryFlagStore(
    private val onChange: (ChangeSet) -> Unit = {},
) : FlagStore {
    private val ref = AtomicReference(Snapshot.EMPTY)
    private val initialized = AtomicReference(false)

    override fun snapshot(): Snapshot = ref.load()

    override val isInitialized: Boolean get() = initialized.load()

    override fun init(newSnapshot: Snapshot) {
        val previous = ref.exchange(newSnapshot)
        initialized.store(true)
        val changedFlags = diffKeys(previous.flags, newSnapshot.flags) { it.version }
        val changedSegments = diffKeys(previous.segments, newSnapshot.segments) { it.version }
        notify(
            Dependencies.affected(newSnapshot, changedFlags, changedSegments) +
                Dependencies.affected(previous, changedFlags, changedSegments),
        )
    }

    override fun upsertFlag(flag: FlagConfig): Boolean =
        update(flag.key, ItemKind.FLAG) { current ->
            val existing = current.flags[flag.key]
            if (existing != null && existing.version >= flag.version) null else current.withFlag(flag)
        }

    override fun upsertSegment(segment: Segment): Boolean =
        update(segment.key, ItemKind.SEGMENT) { current ->
            val existing = current.segments[segment.key]
            if (existing != null && existing.version >= segment.version) null else current.withSegment(segment)
        }

    override fun delete(
        kind: ItemKind,
        key: String,
        version: Long,
    ): Boolean =
        update(key, kind) { current ->
            when (kind) {
                ItemKind.FLAG -> {
                    val existing = current.flags[key]
                    if (existing != null && existing.version >= version) {
                        null
                    } else {
                        current.withFlag(existing?.copy(version = version, deleted = true) ?: FlagConfig(key, version, deleted = true))
                    }
                }
                ItemKind.SEGMENT -> {
                    val existing = current.segments[key]
                    if (existing != null && existing.version >= version) {
                        null
                    } else {
                        current.withSegment(existing?.copy(version = version, deleted = true) ?: Segment(key, version, deleted = true))
                    }
                }
            }
        }

    /** CAS loop; [transform] returns null when the update is stale. */
    private inline fun update(
        key: String,
        kind: ItemKind,
        transform: (Snapshot) -> Snapshot?,
    ): Boolean {
        while (true) {
            val current = ref.load()
            val next = transform(current) ?: return false
            if (ref.compareAndSet(current, next)) {
                val flags = if (kind == ItemKind.FLAG) setOf(key) else emptySet()
                val segments = if (kind == ItemKind.SEGMENT) setOf(key) else emptySet()
                notify(Dependencies.affected(next, flags, segments) + Dependencies.affected(current, flags, segments))
                return true
            }
        }
    }

    private fun notify(flagKeys: Set<String>) {
        if (flagKeys.isNotEmpty()) onChange(ChangeSet(flagKeys))
    }

    private inline fun <T> diffKeys(
        old: Map<String, T>,
        new: Map<String, T>,
        version: (T) -> Long,
    ): Set<String> =
        (old.keys + new.keys).filterTo(HashSet()) { key ->
            val a = old[key]
            val b = new[key]
            a == null || b == null || version(a) != version(b)
        }
}

/** Reverse dependency graph: which flags must be re-evaluated when a flag or segment changes. */
internal object Dependencies {
    fun affected(
        snapshot: Snapshot,
        changedFlags: Set<String>,
        changedSegments: Set<String>,
    ): Set<String> {
        if (changedFlags.isEmpty() && changedSegments.isEmpty()) return emptySet()
        val dependents = HashMap<String, MutableSet<String>>()

        fun edge(
            from: String,
            to: String,
        ) {
            dependents.getOrPut(from) { HashSet() } += to
        }
        for (flag in snapshot.flags.values) {
            flag.prerequisites.forEach { edge("flag:${it.key}", "flag:${flag.key}") }
            flag.rules.flatMap { it.clauses }.filter { it.op == Operator.SEGMENT_MATCH }.forEach { clause ->
                clause.values.forEach { edge("segment:${it.content}", "flag:${flag.key}") }
            }
        }
        for (segment in snapshot.segments.values) {
            segment.rules.flatMap { it.clauses }.filter { it.op == Operator.SEGMENT_MATCH }.forEach { clause ->
                clause.values.forEach { edge("segment:${it.content}", "segment:${segment.key}") }
            }
        }
        val visited = HashSet<String>()
        val queue = ArrayDeque(changedFlags.map { "flag:$it" } + changedSegments.map { "segment:$it" })
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (visited.add(node)) dependents[node]?.let(queue::addAll)
        }
        return visited.filter { it.startsWith("flag:") }.mapTo(HashSet()) { it.removePrefix("flag:") }
    }
}
