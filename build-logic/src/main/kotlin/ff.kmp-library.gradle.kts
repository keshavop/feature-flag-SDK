// Shared Kotlin Multiplatform setup for SDK modules: JVM (backend + Android consumers),
// JS (browser + Node) and iOS. Android-specific targets are added by the modules that need them.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("ff.lint")
}

kotlin {
    explicitApi()

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    js {
        browser {
            testTask { enabled = false }
        }
        nodejs {
            testTask {
                // the bucketing distribution tests hash 1M keys, which can exceed mocha's 2 s default on a busy CI runner
                useMocha { timeout = "60s" }
            }
        }
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
