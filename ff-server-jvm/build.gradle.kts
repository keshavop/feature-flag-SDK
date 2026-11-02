plugins {
    id("ff.jvm-library")
    id("ff.maven-publish")
}

dependencies {
    api(project(":ff-sync"))
    api(project(":ff-events"))
    implementation(project(":ff-transport-okhttp"))
    implementation(libs.slf4j.api)
    // optional: only needed by applications that use MicrometerMetrics
    compileOnly(libs.micrometer.core)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.micrometer.core)
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
}
