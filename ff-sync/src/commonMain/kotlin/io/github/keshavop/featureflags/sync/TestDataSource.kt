package io.github.keshavop.featureflags.sync

import io.github.keshavop.featureflags.core.eval.percentageRollout
import io.github.keshavop.featureflags.core.model.Clause
import io.github.keshavop.featureflags.core.model.FlagConfig
import io.github.keshavop.featureflags.core.model.Operator
import io.github.keshavop.featureflags.core.model.Rule
import io.github.keshavop.featureflags.core.model.Snapshot
import io.github.keshavop.featureflags.core.model.Target
import io.github.keshavop.featureflags.core.model.VariationOrRollout
import io.github.keshavop.featureflags.core.store.FlagStore
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.atomics.AtomicReference

/**
 * Programmatic flags for applications' own unit tests (LLD §7.3). Changes apply immediately to every SDK instance
 * created with this source:
 *
 * ```
 * val td = TestDataSource()
 * val client = FeatureFlagServer.create("key", ServerConfig(dataSource = ServerDataSource.Custom(td)))
 * td.update(td.flag("new-checkout").booleanFlag().fallthroughPercentage(30.0))
 * ```
 */
public class TestDataSource : DataSourceFactory {
    private val flags = AtomicReference(emptyMap<String, FlagConfig>())
    private val stores = AtomicReference(emptyList<FlagStore>())

    /** Builder for [key], starting from its current definition if it exists. */
    public fun flag(key: String): FlagBuilder = FlagBuilder(key, flags.load()[key])

    public fun update(builder: FlagBuilder) {
        while (true) {
            val current = flags.load()
            val flag = builder.build(version = (current[builder.key]?.version ?: 0) + 1)
            if (flags.compareAndSet(current, current + (flag.key to flag))) {
                stores.load().forEach { it.upsertFlag(flag) }
                return
            }
        }
    }

    override fun create(context: DataSourceContext): DataSource {
        while (true) {
            val current = stores.load()
            if (stores.compareAndSet(current, current + context.store)) break
        }
        return StaticDataSource(context.store, Snapshot(flags = flags.load()), context.clock)
    }

    public class FlagBuilder internal constructor(
        public val key: String,
        existing: FlagConfig?,
    ) {
        private var on = existing?.on ?: true
        private var variations: List<JsonElement> = existing?.variations ?: BOOLEAN
        private var offVariation: Int? = existing?.offVariation ?: 0
        private var fallthrough = existing?.fallthrough ?: VariationOrRollout(variation = 0)
        private val targets = (existing?.targets ?: emptyList()).toMutableList()
        private val rules = (existing?.rules ?: emptyList()).toMutableList()

        /** Variations `false` (0) and `true` (1). */
        public fun booleanFlag(): FlagBuilder = variations(BOOLEAN)

        public fun variations(values: List<JsonElement>): FlagBuilder = apply { variations = values }

        public fun variations(vararg values: String): FlagBuilder = variations(values.map(::JsonPrimitive))

        public fun on(value: Boolean): FlagBuilder = apply { on = value }

        public fun offVariation(index: Int): FlagBuilder = apply { offVariation = index }

        public fun fallthroughVariation(index: Int): FlagBuilder = apply { fallthrough = VariationOrRollout(variation = index) }

        /** Boolean flag serving [value] to everyone. */
        public fun variationForAll(value: Boolean): FlagBuilder =
            booleanFlag()
                .on(true)
                .also { targets.clear() }
                .also { rules.clear() }
                .fallthroughVariation(if (value) 1 else 0)

        /** Boolean flag serving `true` to [percent] of contexts (sticky, ADR-006). */
        public fun fallthroughPercentage(percent: Double): FlagBuilder =
            booleanFlag().on(true).apply {
                fallthrough =
                    VariationOrRollout(rollout = percentageRollout(percent, treatment = 1, control = 0))
            }

        public fun variationForKey(
            contextKind: String,
            key: String,
            variation: Int,
        ): FlagBuilder = apply { targets += Target(contextKind, setOf(key), variation) }

        public fun variationForUser(
            userKey: String,
            value: Boolean,
        ): FlagBuilder = booleanFlag().variationForKey("user", userKey, if (value) 1 else 0)

        /** Adds a rule: contexts whose [attribute] is one of [values] get [variation]. */
        public fun ifMatch(
            attribute: String,
            values: List<String>,
            variation: Int,
            contextKind: String = "user",
        ): FlagBuilder =
            apply {
                val clause = Clause(contextKind, attribute, Operator.IN, values.map(::JsonPrimitive))
                rules += Rule("rule-${rules.size}", listOf(clause), VariationOrRollout(variation = variation))
            }

        internal fun build(version: Long): FlagConfig =
            FlagConfig(
                key = key,
                version = version,
                on = on,
                variations = variations,
                offVariation = offVariation,
                targets = targets.toList(),
                rules = rules.toList(),
                fallthrough = fallthrough,
                salt = key,
            )

        private companion object {
            val BOOLEAN = listOf(JsonPrimitive(false), JsonPrimitive(true))
        }
    }
}
