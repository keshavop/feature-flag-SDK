package io.github.keshavop.featureflags.core

import io.github.keshavop.featureflags.core.model.Clause
import io.github.keshavop.featureflags.core.model.FlagConfig
import io.github.keshavop.featureflags.core.model.Operator
import io.github.keshavop.featureflags.core.model.Prerequisite
import io.github.keshavop.featureflags.core.model.Rule
import io.github.keshavop.featureflags.core.model.Segment
import io.github.keshavop.featureflags.core.model.Snapshot
import io.github.keshavop.featureflags.core.store.ChangeSet
import io.github.keshavop.featureflags.core.store.InMemoryFlagStore
import io.github.keshavop.featureflags.core.store.ItemKind
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlagStoreTest {
    private val changes = mutableListOf<ChangeSet>()
    private val store = InMemoryFlagStore { changes += it }

    private val base = FlagConfig("base", version = 1, on = true)
    private val child = FlagConfig("child", version = 1, on = true, prerequisites = listOf(Prerequisite("base", 0)))
    private val segmentUser =
        FlagConfig(
            "uses-segment",
            version = 1,
            rules =
                listOf(
                    Rule("r", listOf(Clause(attribute = "key", op = Operator.SEGMENT_MATCH, values = listOf(JsonPrimitive("beta"))))),
                ),
        )

    @Test
    fun initMarksStoreInitializedAndReportsAllFlags() {
        assertFalse(store.isInitialized)
        store.init(Snapshot(flags = mapOf("base" to base, "child" to child)))
        assertTrue(store.isInitialized)
        assertEquals(setOf("base", "child"), changes.single().flagKeys)
    }

    @Test
    fun olderOrEqualVersionsAreIgnored() {
        store.init(Snapshot(flags = mapOf("base" to base.copy(version = 5))))
        assertFalse(store.upsertFlag(base.copy(version = 5, on = false)))
        assertFalse(store.upsertFlag(base.copy(version = 4, on = false)))
        assertTrue(store.upsertFlag(base.copy(version = 6, on = false)))
        assertFalse(store.snapshot().flag("base")!!.on)
    }

    @Test
    fun deleteLeavesTombstoneThatBlocksResurrection() {
        store.init(Snapshot(flags = mapOf("base" to base)))
        assertTrue(store.delete(ItemKind.FLAG, "base", 3))
        assertNull(store.snapshot().flag("base"))
        assertFalse(store.upsertFlag(base.copy(version = 2)))
        assertNull(store.snapshot().flag("base"))
    }

    @Test
    fun changeSetIncludesDependents() {
        store.init(Snapshot(flags = mapOf("base" to base, "child" to child, "uses-segment" to segmentUser)))
        changes.clear()
        store.upsertFlag(base.copy(version = 2))
        assertEquals(setOf("base", "child"), changes.last().flagKeys)
        store.upsertSegment(Segment("beta", version = 1))
        assertEquals(setOf("uses-segment"), changes.last().flagKeys)
    }

    @Test
    fun unchangedInitReportsNothing() {
        val snapshot = Snapshot(flags = mapOf("base" to base))
        store.init(snapshot)
        changes.clear()
        store.init(snapshot)
        assertTrue(changes.isEmpty())
    }
}
