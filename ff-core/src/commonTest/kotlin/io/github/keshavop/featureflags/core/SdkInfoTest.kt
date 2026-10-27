package io.github.keshavop.featureflags.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SdkInfoTest {
    @Test
    fun versionIsGeneratedFromBuild() {
        assertTrue(SdkInfo.version.isNotBlank())
    }

    @Test
    fun userAgentContainsNameVersionAndPlatform() {
        assertEquals("feature-flag-sdk/${SdkInfo.version} (ios)", SdkInfo.userAgent("ios"))
    }
}
