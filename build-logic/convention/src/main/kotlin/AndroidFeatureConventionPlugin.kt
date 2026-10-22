import com.emirkuljanin.hnfeed.buildlogic.library
import com.emirkuljanin.hnfeed.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A feature module: a Compose UI library with Hilt ViewModels and the design system. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("hnfeed.android.library.compose")
            pluginManager.apply("hnfeed.android.hilt")

            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", libs.library("androidx-compose-material3"))
                add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
            }
        }
    }
}
