import androidx.room3.gradle.RoomExtension
import com.emirkuljanin.hnfeed.buildlogic.library
import com.emirkuljanin.hnfeed.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Room with KSP. Schemas are exported and checked in so every version is reviewable and migrations testable. */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room3")
            pluginManager.apply("com.google.devtools.ksp")

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                add("implementation", libs.library("room-runtime"))
                add("ksp", libs.library("room-compiler"))
                add("testImplementation", libs.library("room-testing"))
            }
        }
    }
}
