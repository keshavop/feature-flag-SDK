package io.github.keshavop.featureflags.core.eval

import io.github.keshavop.featureflags.core.model.Operator
import io.github.keshavop.featureflags.core.model.SafeRegex
import io.github.keshavop.featureflags.core.model.Snapshot
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Single attribute value vs. single clause value. Comparisons are type-strict (a string never equals a number),
 * except dates, which accept epoch milliseconds or RFC 3339 strings.
 */
internal object Operators {
    fun matches(
        op: Operator,
        attribute: JsonPrimitive,
        value: JsonPrimitive,
        snapshot: Snapshot,
    ): Boolean =
        when (op) {
            Operator.IN, Operator.NOT_IN -> jsonEquals(attribute, value)
            Operator.CONTAINS -> strings(attribute, value) { a, v -> a.contains(v) }
            Operator.STARTS_WITH -> strings(attribute, value) { a, v -> a.startsWith(v) }
            Operator.ENDS_WITH -> strings(attribute, value) { a, v -> a.endsWith(v) }
            Operator.MATCHES ->
                strings(attribute, value) { a, v ->
                    (snapshot.regexCache[v] ?: SafeRegex.compile(v))?.containsMatchIn(a) ?: false
                }
            Operator.LESS_THAN -> numbers(attribute, value) { a, v -> a < v }
            Operator.LESS_THAN_OR_EQUAL -> numbers(attribute, value) { a, v -> a <= v }
            Operator.GREATER_THAN -> numbers(attribute, value) { a, v -> a > v }
            Operator.GREATER_THAN_OR_EQUAL -> numbers(attribute, value) { a, v -> a >= v }
            Operator.BEFORE -> dates(attribute, value) { a, v -> a < v }
            Operator.AFTER -> dates(attribute, value) { a, v -> a > v }
            Operator.SEMVER_EQUAL -> semvers(attribute, value) { a, v -> a.compareTo(v) == 0 }
            Operator.SEMVER_LESS_THAN -> semvers(attribute, value) { a, v -> a < v }
            Operator.SEMVER_GREATER_THAN -> semvers(attribute, value) { a, v -> a > v }
            Operator.SEGMENT_MATCH, Operator.UNKNOWN -> false
        }

    fun jsonEquals(
        a: JsonPrimitive,
        b: JsonPrimitive,
    ): Boolean =
        when {
            a.isString || b.isString -> a.isString && b.isString && a.content == b.content
            a.booleanOrNull != null || b.booleanOrNull != null -> a.booleanOrNull == b.booleanOrNull
            else -> a.doubleOrNull != null && a.doubleOrNull == b.doubleOrNull
        }

    private inline fun strings(
        a: JsonPrimitive,
        v: JsonPrimitive,
        test: (String, String) -> Boolean,
    ): Boolean = a.isString && v.isString && test(a.content, v.content)

    private inline fun numbers(
        a: JsonPrimitive,
        v: JsonPrimitive,
        test: (Double, Double) -> Boolean,
    ): Boolean {
        val x = a.number() ?: return false
        val y = v.number() ?: return false
        return test(x, y)
    }

    private inline fun dates(
        a: JsonPrimitive,
        v: JsonPrimitive,
        test: (Long, Long) -> Boolean,
    ): Boolean {
        val x = a.epochMillis() ?: return false
        val y = v.epochMillis() ?: return false
        return test(x, y)
    }

    private inline fun semvers(
        a: JsonPrimitive,
        v: JsonPrimitive,
        test: (Semver, Semver) -> Boolean,
    ): Boolean {
        if (!a.isString || !v.isString) return false
        val x = Semver.parse(a.content) ?: return false
        val y = Semver.parse(v.content) ?: return false
        return test(x, y)
    }

    private fun JsonPrimitive.number(): Double? = if (isString || booleanOrNull != null) null else doubleOrNull

    @OptIn(ExperimentalTime::class)
    private fun JsonPrimitive.epochMillis(): Long? {
        if (!isString) return number()?.toLong()
        return runCatching { Instant.parse(content).toEpochMilliseconds() }.getOrNull()
    }
}
