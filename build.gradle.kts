// AGP has to live on the root classpath next to the Kotlin Gradle plugin (declared below) so both see each other's
// classes. It is added only where an Android SDK exists, so builds without one (or without access to Google's Maven
// repository) keep working; ff-android is only included in that case too (settings.gradle.kts).
buildscript {
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.android.*")
            }
        }
    }
    val fromProperties =
        rootDir.resolve("local.properties").takeIf { it.isFile }?.readLines()
            ?.firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("=")
    val androidSdk = listOf(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"), fromProperties).firstOrNull { !it.isNullOrBlank() }
    if (androidSdk != null && File(androidSdk).isDirectory) {
        dependencies { classpath(libs.android.gradle.plugin) }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

apiValidation {
    // ff-web is published to npm (its surface is the generated .d.ts); benchmarks and samples are not published
    ignoredProjects += listOf("ff-web", "benchmarks", "server-jvm")
    // ff-android can only be built where an Android SDK exists, so its API dump cannot be maintained everywhere yet
    ignoredProjects += "ff-android"
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}
