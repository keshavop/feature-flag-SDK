import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

// iOS SDK shell (LLD §10.2): NSURLSession transport, file cache, UIApplication lifecycle and NWPathMonitor connectivity,
// packaged as FeatureFlags.xcframework for Swift Package Manager. Linking the framework needs macOS + Xcode.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("ff.lint")
    id("ff.maven-publish")
}

kotlin {
    explicitApi()

    val xcframework = XCFramework("FeatureFlags")
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "FeatureFlags"
            isStatic = true
            export(project(":ff-client-common"))
            export(project(":ff-core"))
            xcframework.add(this)
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
        }
        iosMain.dependencies {
            api(project(":ff-client-common"))
            api(project(":ff-core"))
        }
        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
