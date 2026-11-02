package io.github.keshavop.featureflags.events

import io.github.keshavop.featureflags.core.context.ContextKind
import io.github.keshavop.featureflags.core.context.EvaluationContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Removes private attributes before a context leaves the process. Names may be top-level attributes (`email`) or
 * paths into nested objects (`/address/street`). Removed names are listed in `_meta.redactedAttributes`.
 */
internal class ContextRedactor(
    private val config: EventsConfig,
) {
    fun redact(context: EvaluationContext): JsonObject =
        if (context.kinds.size == 1) {
            redactKind(context.kinds.values.single(), includeKind = true)
        } else {
            JsonObject(
                buildMap {
                    put("kind", JsonPrimitive("multi"))
                    context.kinds.values
                        .sortedBy { it.kind }
                        .forEach { put(it.kind, redactKind(it, includeKind = false)) }
                },
            )
        }

    private fun redactKind(
        kind: ContextKind,
        includeKind: Boolean,
    ): JsonObject {
        val private = config.privateAttributes + kind.privateAttributes
        val redacted = mutableListOf<String>()
        val attributes = LinkedHashMap<String, JsonElement>()
        for ((name, value) in kind.attributes) {
            if (config.allAttributesPrivate || name in private || "/$name" in private) {
                redacted += name
                continue
            }
            val nestedPaths = private.filter { it.startsWith("/$name/") }
            attributes[name] = if (nestedPaths.isEmpty()) value else removePaths(value, nestedPaths, "/$name", redacted)
        }
        return JsonObject(
            buildMap {
                if (includeKind) put("kind", JsonPrimitive(kind.kind))
                put("key", JsonPrimitive(kind.key))
                if (kind.anonymous) put("anonymous", JsonPrimitive(true))
                putAll(attributes)
                if (redacted.isNotEmpty()) {
                    put("_meta", JsonObject(mapOf("redactedAttributes" to JsonArray(redacted.sorted().map(::JsonPrimitive)))))
                }
            },
        )
    }

    private fun removePaths(
        value: JsonElement,
        paths: List<String>,
        prefix: String,
        redacted: MutableList<String>,
    ): JsonElement {
        if (value !is JsonObject) return value
        return JsonObject(
            value
                .mapNotNull { (name, child) ->
                    val path = "$prefix/$name"
                    when {
                        path in paths -> {
                            redacted += path
                            null
                        }
                        paths.any { it.startsWith("$path/") } -> name to removePaths(child, paths, path, redacted)
                        else -> name to child
                    }
                }.toMap(),
        )
    }
}
