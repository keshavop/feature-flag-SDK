plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

apiValidation {
    // ff-web is published to npm, its public surface is tracked through the generated .d.ts instead
    ignoredProjects += "ff-web"
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}
