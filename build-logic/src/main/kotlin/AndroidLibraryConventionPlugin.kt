import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `ff.android-library`: applies the Android library and Kotlin Android plugins by id at runtime. Written as a class rather
 * than a precompiled script so build-logic compiles without AGP on machines that have no Android SDK.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("com.android.library")
        project.pluginManager.apply("org.jetbrains.kotlin.android")
    }
}
