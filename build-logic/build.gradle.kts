plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.maven.publish.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    // AGP must share a classloader with the Kotlin plugin, so it lives here too — but only where an Android SDK exists,
    // keeping builds on machines without one (or without access to Google's Maven repository) working.
    if (androidSdkPresent()) implementation(libs.android.gradle.plugin)
}

fun androidSdkPresent(): Boolean {
    val fromProperties =
        rootDir.resolve("../local.properties").takeIf { it.isFile }?.readLines()
            ?.firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("=")
    val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: fromProperties
    return sdk != null && File(sdk).isDirectory
}
