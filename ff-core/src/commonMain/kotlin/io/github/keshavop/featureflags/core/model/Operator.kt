package io.github.keshavop.featureflags.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Clause operators. Operators added by a newer service decode as [UNKNOWN], which never matches. */
@Serializable(with = OperatorSerializer::class)
public enum class Operator {
    IN,
    NOT_IN,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    MATCHES,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    BEFORE,
    AFTER,
    SEMVER_EQUAL,
    SEMVER_LESS_THAN,
    SEMVER_GREATER_THAN,
    SEGMENT_MATCH,
    UNKNOWN,
}

internal object OperatorSerializer : KSerializer<Operator> {
    override val descriptor = PrimitiveSerialDescriptor("io.github.keshavop.featureflags.Operator", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Operator,
    ) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): Operator {
        val name = decoder.decodeString()
        return Operator.entries.firstOrNull { it.name == name } ?: Operator.UNKNOWN
    }
}
