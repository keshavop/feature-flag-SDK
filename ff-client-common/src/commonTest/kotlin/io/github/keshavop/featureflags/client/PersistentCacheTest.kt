package io.github.keshavop.featureflags.client

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.model.EvaluatedSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentCacheTest {
    @Test
    fun keepsMostRecentContextsAndHidesUserIds() =
        runTest {
            val storage = InMemoryKeyValueStore()
            val cache = PersistentCache(storage, "mob-key", maxContexts = 2)
            val users = (1..3).map { EvaluationContext.user("user-$it").build() }
            users.forEach { cache.save(it, CachedSnapshot(savedAtMillis = 1, snapshot = EvaluatedSnapshot())) }
            assertNull(cache.load(users[0]), "oldest context evicted")
            assertNotNull(cache.load(users[1]))
            assertNotNull(cache.load(users[2]))
            val key = cache.keyFor(users[0])
            assertFalse(key.contains("user-1"))
            assertTrue(key.startsWith("ff-snapshot-"))
        }

    @Test
    fun corruptEntriesAreIgnored() =
        runTest {
            val storage = InMemoryKeyValueStore()
            val cache = PersistentCache(storage, "k", 5)
            val user = EvaluationContext.user("u").build()
            storage.write(cache.keyFor(user), "{broken")
            assertNull(cache.load(user))
            assertEquals(null, storage.read("ff-index"))
        }
}
