plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

apiValidation {
    // ff-web is published to npm (its surface is the generated .d.ts); benchmarks and samples are not published
    ignoredProjects += listOf("ff-web", "benchmarks", "server-jvm")
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}
