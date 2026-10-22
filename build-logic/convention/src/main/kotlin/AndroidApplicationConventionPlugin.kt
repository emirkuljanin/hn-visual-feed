import com.android.build.api.dsl.ApplicationExtension
import com.emirkuljanin.hnfeed.buildlogic.configureKotlinAndroid
import com.emirkuljanin.hnfeed.buildlogic.libs
import com.emirkuljanin.hnfeed.buildlogic.versionInt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = libs.versionInt("targetSdk")
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                testOptions.animationsDisabled = true
            }
        }
    }
}
