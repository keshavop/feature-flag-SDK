// OpenTelemetry integration (HLD §11): evaluation hook that records feature_flag.evaluation span events.
plugins {
    id("ff.jvm-library")
    id("ff.maven-publish")
}

dependencies {
    api(project(":ff-core"))
    api(libs.opentelemetry.api)
    testImplementation(libs.opentelemetry.sdk.testing)
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}
