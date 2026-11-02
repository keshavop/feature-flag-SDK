package io.github.keshavop.featureflags.core

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.Bucketer
import io.github.keshavop.featureflags.core.eval.EvaluationReason
import io.github.keshavop.featureflags.core.eval.FlagEvaluator
import io.github.keshavop.featureflags.core.eval.Murmur3
import io.github.keshavop.featureflags.core.model.Snapshot
import io.github.keshavop.featureflags.core.model.SnapshotCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Runs the shared, platform-independent conformance suite in `conformance/` (LLD §12). */
class ConformanceTest {
    private val evaluator = FlagEvaluator()

    @Test
    fun bucketingVectorsMatchReferenceImplementation() {
        val vectors = Json.parseToJsonElement(CONFORMANCE_FILES.getValue("bucketing.json")).jsonObject["vectors"]!!.jsonArray
        assertTrue(vectors.size >= 200, "expected at least 200 vectors")
        for (vector in vectors.map { it.jsonObject }) {
            val input = vector.string("input")
            assertEquals(vector["hash"]!!.jsonPrimitive.long, Murmur3.hash32x86(input).toUInt().toLong(), "hash of '$input'")
            val bucket = Bucketer.bucket(vector.string("keyOrSeed"), vector.string("salt"), vector.string("value"))
            assertEquals(vector["bucket"]!!.jsonPrimitive.int, bucket, "bucket of '$input'")
        }
    }

    @Test
    fun evaluationCases() {
        val files = CONFORMANCE_FILES.filterKeys { it.startsWith("evaluation/") }
        assertTrue(files.isNotEmpty(), "no evaluation vectors found")
        val failures = mutableListOf<String>()
        var count = 0
        for ((file, text) in files) {
            val suite = Json.parseToJsonElement(text).jsonObject
            val snapshot = SnapshotCodec.json.decodeFromJsonElement(Snapshot.serializer(), suite["snapshot"]!!)
            for (case in suite["cases"]!!.jsonArray.map { it.jsonObject }) {
                count++
                val name = "$file: ${case.string("name")}"
                val context = EvaluationContext.fromJson(case["context"]!!.jsonObject)
                val detail = evaluator.evaluate(snapshot, case.string("flag"), context)
                val expect = case["expect"]!!.jsonObject
                expect["value"]?.let { expected ->
                    val actual = detail.value ?: JsonNull
                    if (expected != actual) failures += "$name: value $actual, expected $expected"
                }
                if ("variation" in expect) {
                    val expected = expect["variation"]!!.jsonPrimitive.intOrNull
                    if (expected != detail.variationIndex) failures += "$name: variation ${detail.variationIndex}, expected $expected"
                }
                expect["reason"]?.jsonObject?.let { expected ->
                    val actual = reasonJson(detail.reason)
                    expected.forEach { (field, value) ->
                        if (actual[field] != value) failures += "$name: reason.$field ${actual[field]}, expected $value"
                    }
                }
            }
        }
        if (failures.isNotEmpty()) fail("${failures.size} of $count conformance cases failed:\n" + failures.joinToString("\n"))
    }

    private fun reasonJson(reason: EvaluationReason): JsonObject {
        val fields = mutableMapOf<String, JsonElement>("kind" to JsonPrimitive(reason.kind))
        when (reason) {
            is EvaluationReason.RuleMatch -> {
                fields["ruleId"] = JsonPrimitive(reason.ruleId)
                fields["ruleIndex"] = JsonPrimitive(reason.ruleIndex)
                fields["inExperiment"] = JsonPrimitive(reason.inExperiment)
            }
            is EvaluationReason.Fallthrough -> fields["inExperiment"] = JsonPrimitive(reason.inExperiment)
            is EvaluationReason.PrerequisiteFailed -> fields["prerequisiteKey"] = JsonPrimitive(reason.prerequisiteKey)
            is EvaluationReason.Error -> fields["errorKind"] = JsonPrimitive(reason.errorKind.name)
            EvaluationReason.Off, EvaluationReason.TargetMatch -> Unit
        }
        return JsonObject(fields)
    }

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content
}
