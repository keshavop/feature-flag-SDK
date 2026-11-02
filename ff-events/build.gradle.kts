plugins {
    id("ff.kmp-library")
    id("ff.maven-publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":ff-sync"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
