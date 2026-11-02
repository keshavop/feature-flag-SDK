package io.github.keshavop.featureflags.core

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.FlagEvaluator
import io.github.keshavop.featureflags.core.model.FlagConfig
import io.github.keshavop.featureflags.core.model.Prerequisite
import io.github.keshavop.featureflags.core.model.Snapshot
import io.github.keshavop.featureflags.core.model.VariationOrRollout
import io.github.keshavop.featureflags.core.store.InMemoryFlagStore
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 64 reader threads evaluate while one writer keeps publishing new versions (ADR-005). Parent and child always change
 * together in one snapshot, so a reader that saw a torn (mixed-version) state would get a failed prerequisite.
 */
class FlagStoreConcurrencyTest {
    @Test
    fun readersNeverSeeTornState() {
        val store = InMemoryFlagStore()
        val evaluator = FlagEvaluator()

        fun snapshotAt(version: Long): Snapshot {
            val variation = (version % 2).toInt()
            val parent =
                FlagConfig(
                    "parent",
                    version,
                    on = true,
                    variations = listOf(JsonPrimitive(0), JsonPrimitive(1)),
                    fallthrough = VariationOrRollout(variation),
                )
            val child =
                FlagConfig(
                    "child",
                    version,
                    on = true,
                    variations = listOf(JsonPrimitive("off"), JsonPrimitive("on")),
                    offVariation = 0,
                    prerequisites = listOf(Prerequisite("parent", variation)),
                    fallthrough = VariationOrRollout(1),
                )
            return Snapshot(version = version, flags = mapOf("parent" to parent, "child" to child))
        }
        store.init(snapshotAt(1))

        val readers = 64
        val pool = Executors.newFixedThreadPool(readers + 1)
        val start = CountDownLatch(1)
        val running =
            java.util.concurrent.atomic
                .AtomicBoolean(true)
        val torn = AtomicInteger()
        val evaluations = AtomicInteger()
        val context = EvaluationContext.user("u").build()
        repeat(readers) {
            pool.execute {
                start.await()
                while (running.get()) {
                    val value = evaluator.evaluate(store.snapshot(), "child", context).value
                    if (value != JsonPrimitive("on")) torn.incrementAndGet()
                    evaluations.incrementAndGet()
                }
            }
        }
        pool.execute {
            start.await()
            for (version in 2L..20_000L) store.init(snapshotAt(version))
            running.set(false)
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(60, TimeUnit.SECONDS)
        assertEquals(0, torn.get(), "torn reads out of ${evaluations.get()} evaluations")
    }
}
