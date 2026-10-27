import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("ff.lint")
}

kotlin {
    js {
        outputModuleName.set("feature-flags")
        browser {
            testTask { enabled = false }
        }
        nodejs()
        binaries.library()
        useEsModules()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":ff-core"))
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// The Kotlin/JS library output is wrapped into the npm package published as @keshavop/feature-flags.
// CI runs `npm publish ff-web/build/npm` after this task.
tasks.register<Sync>("prepareNpmPackage") {
    description = "Assembles the publishable npm package in build/npm."
    group = "publishing"
    val npmPackageName = "@keshavop/feature-flags"
    val version = providers.gradleProperty("VERSION_NAME")
    val generatedPackageJson = layout.buildDirectory.file("dist/js/productionLibrary/package.json")
    inputs.property("version", version)
    from(tasks.named("jsProductionLibraryCompileSync"))
    from(rootProject.file("LICENSE"))
    from(layout.projectDirectory.file("npm/README.md"))
    into(layout.buildDirectory.dir("npm"))
    exclude("package.json")
    doLast {
        @Suppress("UNCHECKED_CAST")
        val generated = JsonSlurper().parse(generatedPackageJson.get().asFile) as Map<String, Any?>
        val main = generated.getValue("main")
        val pkg =
            linkedMapOf(
                "name" to npmPackageName,
                "version" to version.get(),
                "description" to "Feature flag SDK for the browser: local evaluation, offline cache, live updates",
                "license" to "MIT",
                "type" to "module",
                "main" to main,
                "types" to generated.getValue("types"),
                "exports" to mapOf("." to mapOf("types" to "./${generated.getValue("types")}", "default" to "./$main")),
                "homepage" to "https://github.com/keshavop/feature-flag-SDK#readme",
                "repository" to
                    mapOf(
                        "type" to "git",
                        "url" to "git+https://github.com/keshavop/feature-flag-SDK.git",
                        "directory" to "ff-web",
                    ),
                "keywords" to listOf("feature-flags", "feature-toggles", "rollout", "experimentation"),
                "sideEffects" to false,
                "publishConfig" to mapOf("access" to "public"),
            )
        destinationDir.resolve("package.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(pkg)) + "\n")
    }
}
