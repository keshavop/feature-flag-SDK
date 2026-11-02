package io.github.keshavop.featureflags.core.eval

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Typed views of a raw evaluation (LLD §4.5). A missing value serves the caller's default with the original reason;
 * a value of the wrong type serves the default with `ERROR(WRONG_TYPE)`.
 */
public object Variations {
    public fun bool(
        detail: EvaluationDetail<JsonElement?>,
        default: Boolean,
    ): EvaluationDetail<Boolean> = typed(detail, default) { (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.booleanOrNull }

    public fun string(
        detail: EvaluationDetail<JsonElement?>,
        default: String,
    ): EvaluationDetail<String> = typed(detail, default) { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }

    public fun int(
        detail: EvaluationDetail<JsonElement?>,
        default: Int,
    ): EvaluationDetail<Int> =
        typed(detail, default) { element ->
            val primitive = (element as? JsonPrimitive)?.takeUnless { it.isString } ?: return@typed null
            val number = primitive.doubleOrNull ?: return@typed null
            primitive.longOrNull?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
                ?: number.takeIf { it == kotlin.math.floor(it) && it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }?.toInt()
        }

    public fun double(
        detail: EvaluationDetail<JsonElement?>,
        default: Double,
    ): EvaluationDetail<Double> = typed(detail, default) { (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.doubleOrNull }

    public fun json(
        detail: EvaluationDetail<JsonElement?>,
        default: JsonElement,
    ): EvaluationDetail<JsonElement> = typed(detail, default) { it }

    private inline fun <T> typed(
        detail: EvaluationDetail<JsonElement?>,
        default: T,
        convert: (JsonElement) -> T?,
    ): EvaluationDetail<T> {
        val raw = detail.value ?: return EvaluationDetail(default, null, detail.reason)
        val value = convert(raw) ?: return EvaluationDetail(default, null, EvaluationReason.error(ErrorKind.WRONG_TYPE))
        return EvaluationDetail(value, detail.variationIndex, detail.reason)
    }
}
