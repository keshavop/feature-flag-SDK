import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Android SDK shell (LLD §10.1): lifecycle- and connectivity-aware client on top of ff-client-common.
// Uses AGP's built-in Kotlin support; no AndroidX dependencies.
plugins {
    id("com.android.library")
    id("ff.lint")
    id("ff.maven-publish")
}

android {
    namespace = "io.github.keshavop.featureflags.android"
    compileSdk = 35
    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xexplicit-api=strict")
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
    }
}

dependencies {
    api(project(":ff-client-common"))
    implementation(project(":ff-transport-okhttp"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(kotlin("test-junit"))
}
