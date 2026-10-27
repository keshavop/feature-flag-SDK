@file:OptIn(ExperimentalJsExport::class)

package io.github.keshavop.featureflags.web

import io.github.keshavop.featureflags.core.SdkInfo

/** Version of the SDK bundled in this npm package. */
@JsExport
public fun sdkVersion(): String = SdkInfo.version
