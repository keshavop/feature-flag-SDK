import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Android SDK shell (LLD §10.1): lifecycle- and connectivity-aware client on top of ff-client-common.
// No AndroidX dependencies.
plugins {
    id("ff.android-library")
    id("ff.lint")
    id("ff.maven-publish")
}

// AGP is loaded through build-logic (only where an Android SDK exists), so its types are not on this script's classpath:
// configure the `android` extension dynamically instead of through the typed accessor.
extensions.getByName("android").withGroovyBuilder {
    "setNamespace"("io.github.keshavop.featureflags.android")
    "setCompileSdk"(35)
    "defaultConfig" {
        "setMinSdk"(21)
        "consumerProguardFiles"("consumer-rules.pro")
    }
    "compileOptions" {
        "setSourceCompatibility"(JavaVersion.VERSION_11)
        "setTargetCompatibility"(JavaVersion.VERSION_11)
    }
    "lint" {
        "setAbortOnError"(true)
        "setWarningsAsErrors"(false)
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
        // explicit API mode for the library code only, not for its unit tests
        if (!name.contains("UnitTest")) freeCompilerArgs.add("-Xexplicit-api=strict")
    }
}

dependencies {
    api(project(":ff-client-common"))
    implementation(project(":ff-transport-okhttp"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(kotlin("test-junit"))
}
