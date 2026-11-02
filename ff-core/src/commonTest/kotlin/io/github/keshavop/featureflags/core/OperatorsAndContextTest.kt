package io.github.keshavop.featureflags.core

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.ErrorKind
import io.github.keshavop.featureflags.core.eval.EvaluationDetail
import io.github.keshavop.featureflags.core.eval.EvaluationReason
import io.github.keshavop.featureflags.core.eval.Murmur3
import io.github.keshavop.featureflags.core.eval.Semver
import io.github.keshavop.featureflags.core.eval.Variations
import io.github.keshavop.featureflags.core.model.Operator
import io.github.keshavop.featureflags.core.model.SnapshotCodec
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperatorsAndContextTest {
    @Test
    fun murmurReferenceValues() {
        // published MurmurHash3_x86_32 test values
        assertEquals(0, Murmur3.hash32x86(""))
        assertEquals(0x514E28B7, Murmur3.hash32x86(ByteArray(0), seed = 1))
        assertEquals(613153351, Murmur3.hash32x86("hello"))
    }

    @Test
    fun semverOrdering() {
        val ordered =
            listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.2", "2")
        val parsed = ordered.map { requireNotNull(Semver.parse(it)) { it } }
        assertEquals(parsed, parsed.sorted())
        assertNull(Semver.parse("not-a-version"))
        assertEquals(0, Semver.parse("1.2.3+build.5")!!.compareTo(Semver.parse("v1.2.3")!!))
    }

    @Test
    fun unknownOperatorDecodesInsteadOfFailing() {
        val flag =
            SnapshotCodec.decodeFlag(
                """{"key":"f","version":1,"rules":[{"id":"r","clauses":[{"attribute":"a","op":"FUZZY","values":[]}]}],"newField":1}""",
            )
        assertEquals(
            Operator.UNKNOWN,
            flag.rules
                .single()
                .clauses
                .single()
                .op,
        )
    }

    @Test
    fun contextJsonRoundTrip() {
        val user =
            EvaluationContext
                .user("u-42")
                .set("email", "a@b.c")
                .private("email")
                .build()
        val org = EvaluationContext.builder("organization", "o1").set("seats", 40).build()
        val multi = EvaluationContext.multi(user, org)
        assertEquals("organization:o1|user:u-42", multi.fullyQualifiedKey)
        assertEquals(multi, EvaluationContext.fromJson(multi.toJson()))
        assertEquals(setOf("email"), EvaluationContext.fromJson(user.toJson()).kind("user")!!.privateAttributes)
    }

    @Test
    fun contextValidation() {
        assertFailsWith<IllegalArgumentException> { EvaluationContext.user(" ").build() }
        assertFailsWith<IllegalArgumentException> { EvaluationContext.builder("multi", "x").build() }
        assertFailsWith<IllegalArgumentException> { EvaluationContext.user("x").set("key", "y") }
        val u = EvaluationContext.user("a").build()
        assertFailsWith<IllegalArgumentException> { EvaluationContext.multi(u, u) }
    }

    @Test
    fun typedConversions() {
        fun detail(value: JsonElement?) = EvaluationDetail(value, 0, EvaluationReason.FALLTHROUGH)
        assertEquals(true, Variations.bool(detail(JsonPrimitive(true)), false).value)
        assertEquals(EvaluationReason.error(ErrorKind.WRONG_TYPE), Variations.bool(detail(JsonPrimitive("true")), false).reason)
        assertEquals(3, Variations.int(detail(JsonPrimitive(3.0)), 0).value)
        assertEquals(EvaluationReason.error(ErrorKind.WRONG_TYPE), Variations.int(detail(JsonPrimitive(3.5)), 0).reason)
        assertEquals(2.5, Variations.double(detail(JsonPrimitive(2.5)), 0.0).value)
        assertEquals("x", Variations.string(detail(null), "x").value)
        assertTrue(Variations.string(detail(null), "x").isDefaultValue)
    }
}
