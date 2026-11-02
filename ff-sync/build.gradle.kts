plugins {
    id("ff.kmp-library")
    id("ff.maven-publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":ff-core"))
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
