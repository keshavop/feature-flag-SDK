plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}
