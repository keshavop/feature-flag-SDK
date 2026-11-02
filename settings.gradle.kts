pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "feature-flag-sdk"

include(":ff-core")
include(":ff-sync")
include(":ff-events")
include(":ff-server-jvm")
include(":ff-web")
include(":benchmarks")
include(":samples:server-jvm")
