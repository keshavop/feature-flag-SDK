package io.github.keshavop.featureflags.core.context

import io.github.keshavop.featureflags.core.model.ContextKinds
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.jvm.JvmStatic

/**
 * Who a flag is evaluated for: one or more kinds (user, device, organization, …), each with a key and attributes.
 * Immutable; build with [user], [builder] or [multi].
 */
public class EvaluationContext private constructor(
    public val kinds: Map<String, ContextKind>,
) {
    /** Stable identity used for cache keys and event de-duplication, e.g. `user:u-42` or `org:o1|user:u-42`. */
    public val fullyQualifiedKey: String =
        kinds.values.sortedBy { it.kind }.joinToString("|") { "${it.kind}:${it.key}" }

    public val isMulti: Boolean get() = kinds.size > 1

    public fun kind(kind: String): ContextKind? = kinds[kind]

    /** JSON form used on the wire (LLD §5): a single-kind object or `{"kind":"multi", "<kind>": {…}}`. */
    public fun toJson(): JsonObject =
        if (kinds.size == 1) {
            kinds.values.single().toJson(includeKind = true)
        } else {
            JsonObject(
                buildMap {
                    put("kind", JsonPrimitive(ContextKinds.MULTI))
                    kinds.values.sortedBy { it.kind }.forEach { put(it.kind, it.toJson(includeKind = false)) }
                },
            )
        }

    override fun equals(other: Any?): Boolean = other is EvaluationContext && other.kinds == kinds

    override fun hashCode(): Int = kinds.hashCode()

    override fun toString(): String = "EvaluationContext($fullyQualifiedKey)"

    public class Builder(
        private val kind: String,
        private val key: String,
    ) {
        private val attributes = LinkedHashMap<String, JsonElement>()
        private val privateAttributes = LinkedHashSet<String>()
        private var anonymous = false

        public fun set(
            name: String,
            value: String,
        ): Builder = set(name, JsonPrimitive(value))

        public fun set(
            name: String,
            value: Number,
        ): Builder = set(name, JsonPrimitive(value))

        public fun set(
            name: String,
            value: Boolean,
        ): Builder = set(name, JsonPrimitive(value))

        public fun set(
            name: String,
            value: JsonElement,
        ): Builder =
            apply {
                require(name !in RESERVED) { "'$name' is a reserved context attribute" }
                attributes[name] = value
            }

        /** Attributes used for evaluation but never sent in events. */
        public fun private(vararg names: String): Builder = apply { privateAttributes += names }

        public fun anonymous(value: Boolean = true): Builder = apply { anonymous = value }

        public fun buildKind(): ContextKind {
            require(key.isNotBlank()) { "context key must not be blank" }
            require(KIND_PATTERN.matches(kind)) { "invalid context kind '$kind'" }
            require(kind != ContextKinds.MULTI) { "'multi' is not a valid single context kind" }
            return ContextKind(kind, key, anonymous, attributes.toMap(), privateAttributes.toSet())
        }

        public fun build(): EvaluationContext = EvaluationContext(mapOf(kind to buildKind()))
    }

    public companion object {
        private val KIND_PATTERN = Regex("[a-zA-Z0-9._-]+")
        internal val RESERVED = setOf("kind", "key", "anonymous", "_meta")

        @JvmStatic
        public fun user(key: String): Builder = Builder(ContextKinds.USER, key)

        @JvmStatic
        public fun builder(
            kind: String,
            key: String,
        ): Builder = Builder(kind, key)

        /** Combines single-kind contexts, e.g. a user and the organization they belong to. */
        @JvmStatic
        public fun multi(vararg contexts: EvaluationContext): EvaluationContext {
            val merged = LinkedHashMap<String, ContextKind>()
            for (context in contexts) {
                for ((kind, value) in context.kinds) {
                    require(merged.put(kind, value) == null) { "context kind '$kind' given more than once" }
                }
            }
            require(merged.isNotEmpty()) { "a multi context needs at least one kind" }
            return EvaluationContext(merged)
        }

        /** Parses the wire JSON form produced by [toJson]. */
        @JvmStatic
        public fun fromJson(json: JsonObject): EvaluationContext {
            val kind = json["kind"]?.jsonPrimitive?.contentOrNull ?: ContextKinds.USER
            if (kind != ContextKinds.MULTI) return EvaluationContext(mapOf(kind to parseKind(kind, json)))
            val kinds = json.filterKeys { it != "kind" }.mapValues { (name, value) -> parseKind(name, value.jsonObject) }
            require(kinds.isNotEmpty()) { "a multi context needs at least one kind" }
            return EvaluationContext(kinds)
        }

        private fun parseKind(
            kind: String,
            json: JsonObject,
        ): ContextKind {
            val key = requireNotNull(json["key"]?.jsonPrimitive?.contentOrNull) { "context '$kind' has no key" }
            val builder = Builder(kind, key)
            json["anonymous"]?.jsonPrimitive?.booleanOrNull?.let { builder.anonymous(it) }
            (json["_meta"] as? JsonObject)?.get("privateAttributes")?.jsonArray?.forEach {
                builder.private(it.jsonPrimitive.content)
            }
            json.filterKeys { it !in RESERVED }.forEach { (name, value) -> builder.set(name, value) }
            return builder.buildKind()
        }
    }
}

/** One kind inside an [EvaluationContext]. */
public data class ContextKind(
    val kind: String,
    val key: String,
    val anonymous: Boolean,
    val attributes: Map<String, JsonElement>,
    val privateAttributes: Set<String>,
) {
    /**
     * Value of a built-in (`key`, `kind`, `anonymous`) or custom attribute. A name starting with `/` is a path into
     * nested objects (`/address/city`). Returns null when the attribute is absent.
     */
    public fun resolve(attribute: String): JsonElement? {
        when (attribute) {
            "key" -> return JsonPrimitive(key)
            "kind" -> return JsonPrimitive(kind)
            "anonymous" -> return JsonPrimitive(anonymous)
        }
        if (!attribute.startsWith("/")) return attributes[attribute]?.takeUnless { it is JsonNull }
        val path = attribute.removePrefix("/").split("/").map { it.replace("~1", "/").replace("~0", "~") }
        var current: JsonElement? = resolve(path.first())
        for (segment in path.drop(1)) current = (current as? JsonObject)?.get(segment)
        return current?.takeUnless { it is JsonNull }
    }

    /** String form used for percentage bucketing: strings as-is, integral numbers in decimal, else null. */
    public fun bucketableValue(attribute: String): String? {
        val value = resolve(attribute) as? JsonPrimitive ?: return null
        if (value.isString) return value.content
        return value.longOrNull?.toString()
    }

    internal fun toJson(includeKind: Boolean): JsonObject =
        JsonObject(
            buildMap {
                if (includeKind) put("kind", JsonPrimitive(kind))
                put("key", JsonPrimitive(key))
                if (anonymous) put("anonymous", JsonPrimitive(true))
                putAll(attributes)
                if (privateAttributes.isNotEmpty()) {
                    put("_meta", JsonObject(mapOf("privateAttributes" to JsonArray(privateAttributes.map(::JsonPrimitive)))))
                }
            },
        )
}
