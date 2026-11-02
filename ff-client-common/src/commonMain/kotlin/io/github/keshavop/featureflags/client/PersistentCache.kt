package io.github.keshavop.featureflags.client

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.Murmur3
import io.github.keshavop.featureflags.core.model.EvaluatedSnapshot
import io.github.keshavop.featureflags.core.model.Snapshot
import io.github.keshavop.featureflags.core.model.SnapshotCodec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** String key-value persistence; each platform provides one (files, IndexedDB/localStorage, …). */
public interface KeyValueStore {
    public suspend fun read(key: String): String?

    public suspend fun write(
        key: String,
        value: String,
    )

    public suspend fun delete(key: String)
}

public class InMemoryKeyValueStore : KeyValueStore {
    private val values = HashMap<String, String>()
    private val lock = Mutex()

    override suspend fun read(key: String): String? = lock.withLock { values[key] }

    override suspend fun write(
        key: String,
        value: String,
    ): Unit = lock.withLock { values[key] = value }

    override suspend fun delete(key: String): Unit = lock.withLock { values.remove(key) }
}

@Serializable
internal data class CachedSnapshot(
    val schemaVersion: Int = Snapshot.SCHEMA_VERSION,
    val etag: String? = null,
    val savedAtMillis: Long,
    val snapshot: EvaluatedSnapshot,
)

/**
 * Last-known-good snapshot per context (LLD §8), so a cold start serves values without the network. Keys are hashes of
 * client key + context, so user ids never appear in storage keys. Keeps the [maxContexts] most recently used contexts.
 */
internal class PersistentCache(
    private val storage: KeyValueStore,
    private val clientKey: String,
    private val maxContexts: Int,
) {
    private val lock = Mutex()
    private val indexSerializer = ListSerializer(String.serializer())

    fun keyFor(context: EvaluationContext): String {
        val text = "$clientKey|${context.fullyQualifiedKey}"
        val high =
            Murmur3
                .hash32x86(text, seed = 1)
                .toUInt()
                .toString(16)
                .padStart(8, '0')
        val low =
            Murmur3
                .hash32x86(text, seed = 2)
                .toUInt()
                .toString(16)
                .padStart(8, '0')
        return "$PREFIX$high$low"
    }

    suspend fun load(context: EvaluationContext): CachedSnapshot? {
        val text = storage.read(keyFor(context)) ?: return null
        val cached = runCatching { SnapshotCodec.json.decodeFromString(CachedSnapshot.serializer(), text) }.getOrNull() ?: return null
        return cached.takeIf { it.schemaVersion == Snapshot.SCHEMA_VERSION }
    }

    suspend fun save(
        context: EvaluationContext,
        cached: CachedSnapshot,
    ) = lock.withLock {
        val key = keyFor(context)
        storage.write(key, SnapshotCodec.json.encodeToString(CachedSnapshot.serializer(), cached))
        val index = listOf(key) + readIndex().filter { it != key }
        index.drop(maxContexts).forEach { storage.delete(it) }
        storage.write(INDEX_KEY, SnapshotCodec.json.encodeToString(indexSerializer, index.take(maxContexts)))
    }

    private suspend fun readIndex(): List<String> =
        storage.read(INDEX_KEY)?.let { runCatching { SnapshotCodec.json.decodeFromString(indexSerializer, it) }.getOrNull() } ?: emptyList()

    private companion object {
        const val PREFIX = "ff-snapshot-"
        const val INDEX_KEY = "ff-index"
    }
}
