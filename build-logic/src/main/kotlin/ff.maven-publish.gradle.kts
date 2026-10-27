// Publishes a module to Maven Central. Coordinates and POM come from gradle.properties
// (GROUP, VERSION_NAME, POM_*); artifacts are named feature-flags-<module> (ff-core -> feature-flags-core).
//
// Credentials are read from Gradle properties, normally supplied by CI as environment variables:
//   ORG_GRADLE_PROJECT_mavenCentralUsername / ORG_GRADLE_PROJECT_mavenCentralPassword
//   ORG_GRADLE_PROJECT_signingInMemoryKey / ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates(artifactId = "feature-flags-${project.name.removePrefix("ff-")}")
}
