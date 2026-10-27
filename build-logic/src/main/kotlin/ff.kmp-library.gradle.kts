// Shared Kotlin Multiplatform setup for SDK modules: JVM (backend + Android consumers),
// JS (browser + Node) and iOS. Android-specific targets are added by the modules that need them.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
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
        nodejs()
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
