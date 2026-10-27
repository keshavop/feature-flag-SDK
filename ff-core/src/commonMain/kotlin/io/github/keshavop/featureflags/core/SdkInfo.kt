package io.github.keshavop.featureflags.core

/**
 * Identifies this SDK build. Sent with every request (User-Agent) and in diagnostic events
 * so the delivery service can tell platforms and versions apart.
 */
public object SdkInfo {
    public const val NAME: String = "feature-flag-sdk"

    public val version: String = SDK_VERSION

    /** Value for the `User-Agent` header, e.g. `feature-flag-sdk/0.1.0 (jvm)`. */
    public fun userAgent(platform: String): String = "$NAME/$version ($platform)"
}
