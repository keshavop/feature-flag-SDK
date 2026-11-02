package io.github.keshavop.featureflags.core

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.Bucketer
import io.github.keshavop.featureflags.core.eval.FlagEvaluator
import io.github.keshavop.featureflags.core.eval.Percent
import io.github.keshavop.featureflags.core.eval.Variations
import io.github.keshavop.featureflags.core.eval.percentageRollout
import io.github.keshavop.featureflags.core.model.FlagConfig
import io.github.keshavop.featureflags.core.model.Snapshot
import io.github.keshavop.featureflags.core.model.VariationOrRollout
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RolloutTest {
    private val evaluator = FlagEvaluator()

    private fun flagAt(percent: Double) =
        FlagConfig(
            key = "new-checkout",
            version = 1,
            on = true,
            salt = "3f9c2a",
            variations = listOf(JsonPrimitive(false), JsonPrimitive(true)),
            fallthrough = VariationOrRollout(rollout = percentageRollout(percent, treatment = 1, control = 0)),
        )

    private fun servedTrue(
        flag: FlagConfig,
        key: String,
    ): Boolean {
        val snapshot = Snapshot(flags = mapOf(flag.key to flag))
        return Variations.bool(evaluator.evaluate(snapshot, flag.key, EvaluationContext.user(key).build()), false).value
    }

    @Test
    fun percentToWeight() {
        assertEquals(1_000, Percent.toWeight(1.0))
        assertEquals(30_000, Percent.toWeight(30.0))
        assertEquals(500, Percent.toWeight(0.5))
        assertEquals(1, Percent.toWeight(0.001))
        assertFailsWith<IllegalArgumentException> { Percent.toWeight(100.5) }
    }

    @Test
    fun rampingUpNeverRemovesIncludedUsers() {
        val stages = listOf(1.0, 5.0, 30.0, 50.0, 100.0).map(::flagAt)
        repeat(20_000) { i ->
            val key = "user-$i"
            var included = false
            for (stage in stages) {
                val now = servedTrue(stage, key)
                assertTrue(now || !included, "$key dropped out at ${stage.fallthrough.rollout!!.weights.first().weight}")
                included = now
            }
            assertTrue(included, "$key not included at 100 %")
        }
    }

    @Test
    fun onePercentServesAboutOnePercent() {
        val n = 1_000_000
        val inTreatment = (0 until n).count { Bucketer.bucket("new-checkout", "3f9c2a", "user-$it") < 1_000 }
        val share = inTreatment.toDouble() / n
        assertTrue(abs(share - 0.01) < 0.0005, "1 % rollout served ${share * 100} %")
    }

    @Test
    fun bucketsAreUniformAcrossDeciles() {
        val n = 1_000_000
        val deciles = IntArray(10)
        for (i in 0 until n) deciles[Bucketer.bucket("dark-mode", "s", "k$i") / 10_000]++
        deciles.forEachIndexed { index, count ->
            val share = count.toDouble() / n
            assertTrue(abs(share - 0.1) < 0.003, "decile $index has ${share * 100} %")
        }
    }

    @Test
    fun sameUserGetsSameAnswerEveryTime() {
        val flag = flagAt(30.0)
        val first = servedTrue(flag, "sticky-user")
        repeat(100) { assertEquals(first, servedTrue(flag, "sticky-user")) }
    }
}
