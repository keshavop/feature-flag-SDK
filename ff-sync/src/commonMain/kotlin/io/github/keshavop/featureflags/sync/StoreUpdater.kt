package io.github.keshavop.featureflags.sync

import io.github.keshavop.featureflags.core.model.FlagConfig
import io.github.keshavop.featureflags.core.model.Segment
import io.github.keshavop.featureflags.core.model.Snapshot
import io.github.keshavop.featureflags.core.model.SnapshotCodec
import io.github.keshavop.featureflags.core.store.FlagStore
import io.github.keshavop.featureflags.core.store.ItemKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Applies server-SDK payloads (LLD §5.1) to a [FlagStore]: `put` replaces everything, `patch` upserts one flag or
 * segment, `delete` stores a tombstone. Throws [InvalidPayloadException] for anything it cannot apply, so the caller can
 * restart the stream rather than continue with partial state.
 */
public class StoreUpdater(
    private val store: FlagStore,
) {
    public fun applyPut(data: String) {
        val snapshot = decode { SnapshotCodec.decode(data) }
        if (snapshot.schemaVersion != Snapshot.SCHEMA_VERSION) {
            throw InvalidPayloadException("unsupported schemaVersion ${snapshot.schemaVersion}")
        }
        store.init(snapshot)
    }

    public fun applyPatch(data: String) {
        val payload = decode { SnapshotCodec.json.parseToJsonElement(data).jsonObject }
        val (kind, key) = parsePath(payload)
        val item = payload["data"] as? JsonObject ?: throw InvalidPayloadException("patch without data")
        when (kind) {
            ItemKind.FLAG ->
                store.upsertFlag(
                    decode { SnapshotCodec.json.decodeFromJsonElement(FlagConfig.serializer(), item) }.check(key) { it.key },
                )
            ItemKind.SEGMENT ->
                store.upsertSegment(
                    decode {
                        SnapshotCodec.json.decodeFromJsonElement(Segment.serializer(), item)
                    }.check(key) { it.key },
                )
        }
    }

    public fun applyDelete(data: String) {
        val payload = decode { SnapshotCodec.json.parseToJsonElement(data).jsonObject }
        val (kind, key) = parsePath(payload)
        val version = decode { payload.getValue("version").jsonPrimitive.long }
        store.delete(kind, key, version)
    }

    /** Applies one SSE event; returns true for `put`, which (re)initializes the store. Unknown event types are ignored. */
    public fun apply(event: SseEvent): Boolean {
        when (event.event) {
            "put" -> {
                applyPut(event.data)
                return true
            }
            "patch" -> applyPatch(event.data)
            "delete" -> applyDelete(event.data)
        }
        return false
    }

    private fun parsePath(payload: JsonObject): Pair<ItemKind, String> {
        val path = payload["path"]?.jsonPrimitive?.content ?: throw InvalidPayloadException("missing path")
        return when {
            path.startsWith("/flags/") -> ItemKind.FLAG to path.removePrefix("/flags/")
            path.startsWith("/segments/") -> ItemKind.SEGMENT to path.removePrefix("/segments/")
            else -> throw InvalidPayloadException("unknown path '$path'")
        }
    }

    private inline fun <T> T.check(
        key: String,
        actual: (T) -> String,
    ): T = also { if (actual(it) != key) throw InvalidPayloadException("path key '$key' does not match item key '${actual(it)}'") }

    private inline fun <T> decode(block: () -> T): T =
        try {
            block()
        } catch (e: InvalidPayloadException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            throw InvalidPayloadException(e.message ?: "malformed payload", e)
        }
}

public class InvalidPayloadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
