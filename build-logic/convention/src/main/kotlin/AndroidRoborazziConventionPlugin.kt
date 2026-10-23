import com.android.build.api.dsl.CommonExtension
import com.emirkuljanin.hnfeed.buildlogic.library
import com.emirkuljanin.hnfeed.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Screenshot tests with Roborazzi on Robolectric: Compose rendered on the JVM, compared against checked-in PNGs.
 * Record with `recordRoborazziDebug`, compare with `verifyRoborazziDebug`.
 */
class AndroidRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")

            extensions.configure<CommonExtension> {
                testOptions.unitTests.isIncludeAndroidResources = true
                testOptions.unitTests.all {
                    // Render through the hardware pipeline so shadows and elevation show up in screenshots.
                    it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                    // Relative times ("Yesterday") are computed in the default time zone; pin it so output is the same
                    // on every machine.
                    it.systemProperty("user.timezone", "UTC")
                    // With Android resources included, Robolectric boots its API 36 runtime, whose
                    // com.android.internal.os.ApplicationSharedMemory reflects into jdk.internal.access,
                    // which the JDK hides since 17.
                    it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
                }
            }

            dependencies {
                add("testImplementation", libs.library("androidx-test-core"))
                add("testImplementation", libs.library("robolectric"))
                add("testImplementation", libs.library("roborazzi"))
            }

            // Compose screenshot and UI tests, only for modules that use Compose (others capture plain bitmaps).
            pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
                dependencies {
                    add("testImplementation", libs.library("androidx-compose-ui-test-junit4"))
                    // Compose UI test pulls an old Espresso (3.5) that fails on the API 36 runtime; use the current one.
                    add("testImplementation", libs.library("androidx-test-espresso-core"))
                    add("testImplementation", libs.library("roborazzi-compose"))
                    add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
                }
            }
        }
    }
}
