package io.github.keshavop.featureflags.core

import io.github.keshavop.featureflags.core.model.FlagConfig
import io.github.keshavop.featureflags.core.store.InMemoryFlagStore
import io.github.keshavop.featureflags.core.store.ItemKind
import org.jetbrains.lincheck.datastructures.IntGen
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.Param
import org.jetbrains.lincheck.datastructures.StressOptions
import kotlin.test.Test

/**
 * Linearizability of [InMemoryFlagStore] (LLD §12): every concurrent history of upserts, deletes and reads must match
 * some sequential order of the same operations.
 */
@Param(name = "version", gen = IntGen::class, conf = "1:6")
class FlagStoreLincheckTest {
    private val store = InMemoryFlagStore()

    @Operation
    fun upsert(
        @Param(name = "version") version: Int,
    ): Boolean = store.upsertFlag(FlagConfig("f", version.toLong(), on = version % 2 == 0))

    @Operation
    fun delete(
        @Param(name = "version") version: Int,
    ): Boolean = store.delete(ItemKind.FLAG, "f", version.toLong())

    @Operation
    fun read(): String = store.snapshot().flags["f"]?.let { "${it.version}:${it.deleted}:${it.on}" } ?: "none"

    @Test
    fun modelChecking() = ModelCheckingOptions().iterations(30).invocationsPerIteration(2_000).check(this::class)

    @Test
    fun stress() = StressOptions().iterations(30).invocationsPerIteration(2_000).check(this::class)
}
