// JVM-only SDK modules (the backend SDK and its adapters). Targets Java 11 bytecode like the KMP JVM target.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("ff.lint")
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    "testImplementation"(kotlin("test"))
}

// API docs; the Dokka plugin comes from the root project's classpath
pluginManager.apply("org.jetbrains.dokka")
