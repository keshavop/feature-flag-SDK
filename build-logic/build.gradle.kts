plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.maven.publish.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
}
