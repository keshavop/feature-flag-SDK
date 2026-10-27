package io.github.keshavop.featureflags.web

import io.github.keshavop.featureflags.core.SdkInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureFlagsWebTest {
    @Test
    fun exposesCoreVersion() {
        assertEquals(SdkInfo.version, sdkVersion())
    }
}
