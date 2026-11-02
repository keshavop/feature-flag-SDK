package io.github.keshavop.featureflags.benchmarks

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.FlagEvaluator
import io.github.keshavop.featureflags.core.eval.percentageRollout
import io.github.keshavop.featureflags.core.model.Clause
import io.github.keshavop.featureflags.core.model.FlagConfig
import io.github.keshavop.featureflags.core.model.Operator
import io.github.keshavop.featureflags.core.model.Rule
import io.github.keshavop.featureflags.core.model.Segment
import io.github.keshavop.featureflags.core.model.SegmentRule
import io.github.keshavop.featureflags.core.model.Snapshot
import io.github.keshavop.featureflags.core.model.VariationOrRollout
import io.github.keshavop.featureflags.core.store.InMemoryFlagStore
import kotlinx.serialization.json.JsonPrimitive
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads

/** Evaluation latency for typical flag shapes, reading through the store like the server SDK does. */
@State(Scope.Benchmark)
open class EvaluationBenchmark {
    private val bool = listOf(JsonPrimitive(false), JsonPrimitive(true))
    private val store =
        InMemoryFlagStore().apply {
            init(
                Snapshot(
                    flags =
                        mapOf(
                            "simple" to FlagConfig("simple", 1, on = true, variations = bool, fallthrough = VariationOrRollout(1)),
                            "rules" to
                                FlagConfig(
                                    "rules",
                                    1,
                                    on = true,
                                    variations = bool,
                                    rules =
                                        List(10) { i ->
                                            Rule("r$i", listOf(Clause(attribute = "country", op = Operator.IN, values = listOf(JsonPrimitive("C$i")))), VariationOrRollout(1))
                                        },
                                    fallthrough = VariationOrRollout(0),
                                ),
                            "rollout" to
                                FlagConfig("rollout", 1, on = true, salt = "s", variations = bool, fallthrough = VariationOrRollout(rollout = percentageRollout(30.0, 1, 0))),
                            "segment" to
                                FlagConfig(
                                    "segment",
                                    1,
                                    on = true,
                                    variations = bool,
                                    rules = listOf(Rule("seg", listOf(Clause(attribute = "key", op = Operator.SEGMENT_MATCH, values = listOf(JsonPrimitive("beta")))), VariationOrRollout(1))),
                                    fallthrough = VariationOrRollout(0),
                                ),
                        ),
                    segments =
                        mapOf(
                            "beta" to
                                Segment(
                                    "beta",
                                    1,
                                    included = (0 until 10_000).map { "user-$it" }.toSet(),
                                    rules = listOf(SegmentRule("pro", listOf(Clause(attribute = "plan", op = Operator.IN, values = listOf(JsonPrimitive("pro")))))),
                                ),
                        ),
                ),
            )
        }
    private val evaluator = FlagEvaluator()
    private val user = EvaluationContext.user("user-123456").set("country", "C9").set("plan", "pro").build()

    @Benchmark
    fun simpleFlag(): Any? = evaluator.evaluate(store.snapshot(), "simple", user).value

    @Benchmark
    fun tenRulesLastMatches(): Any? = evaluator.evaluate(store.snapshot(), "rules", user).value

    @Benchmark
    fun percentageRollout(): Any? = evaluator.evaluate(store.snapshot(), "rollout", user).value

    @Benchmark
    fun segmentRule(): Any? = evaluator.evaluate(store.snapshot(), "segment", user).value

    @Benchmark
    @Threads(8)
    fun simpleFlag8Threads(): Any? = evaluator.evaluate(store.snapshot(), "simple", user).value
}
