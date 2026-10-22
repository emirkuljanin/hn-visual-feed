import com.emirkuljanin.hnfeed.buildlogic.library
import com.emirkuljanin.hnfeed.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            dependencies {
                add("implementation", libs.library("hilt-android"))
                add("ksp", libs.library("hilt-compiler"))
                add("androidTestImplementation", libs.library("hilt-android-testing"))
                add("kspAndroidTest", libs.library("hilt-compiler"))
            }
        }
    }
}
