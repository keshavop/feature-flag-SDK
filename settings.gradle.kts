pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.android.*")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // only Android artifacts come from Google's repository
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.android.*")
            }
        }
    }
}

rootProject.name = "feature-flag-sdk"

include(":ff-core")
include(":ff-sync")
include(":ff-events")
include(":ff-transport-okhttp")
include(":ff-server-jvm")
include(":ff-client-common")
include(":ff-web")
include(":benchmarks")
include(":samples:server-jvm")

// The Android SDK module needs an Android SDK (CI runners have one); other modules build without it.
val androidSdk =
    providers.environmentVariable("ANDROID_HOME").orNull?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable("ANDROID_SDK_ROOT").orNull?.takeIf { it.isNotBlank() }
        ?: file("local.properties").takeIf { it.isFile }?.readLines()?.firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("=")
if (androidSdk != null && file(androidSdk).isDirectory) {
    include(":ff-android")
}
