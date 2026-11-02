package io.github.keshavop.featureflags.ios

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosPlatformTest {
    @Test
    fun fileStoreRoundTrip() =
        runTest {
            val dir = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent("ff-test-${NSUUID().UUIDString}")!!
            val store = IosFileStore(dir)
            assertNull(store.read("ff-snapshot-1"))
            store.write("ff-snapshot-1", """{"a":1}""")
            assertEquals("""{"a":1}""", store.read("ff-snapshot-1"))
            store.delete("ff-snapshot-1")
            assertNull(store.read("ff-snapshot-1"))
        }

    @Test
    fun swiftValuesBecomeContextAttributes() {
        val context = FeatureFlags.userContext("u1", mapOf("plan" to "pro", "seats" to 40, "beta" to true))
        val user = context.kind("user")!!
        assertEquals(JsonPrimitive("pro"), user.attributes["plan"])
        assertEquals(JsonPrimitive(40), user.attributes["seats"])
        assertEquals(JsonPrimitive(true), user.attributes["beta"])
    }
}
