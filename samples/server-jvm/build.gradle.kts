// Runnable example of the server SDK with an in-process TestDataSource. Not published. Run: ./gradlew :samples:server-jvm:run
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":ff-server-jvm"))
    runtimeOnly(libs.slf4j.simple)
}

application {
    mainClass.set("io.github.keshavop.featureflags.samples.ServerSampleKt")
}
