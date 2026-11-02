plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.maven.publish.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    // AGP itself is on the root project's classpath (see the root build.gradle.kts)
}

gradlePlugin {
    plugins {
        create("androidLibrary") {
            id = "ff.android-library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
    }
}
