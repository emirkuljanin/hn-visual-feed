import com.emirkuljanin.hnfeed.buildlogic.buildConfigSecret

plugins {
    alias(libs.plugins.hnfeed.android.library)
    alias(libs.plugins.hnfeed.android.hilt)
    alias(libs.plugins.hnfeed.android.roborazzi)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.emirkuljanin.hnfeed.core.imagegen"

    buildFeatures.buildConfig = true
    // MediaPipe memory-maps the model straight from the APK, which only works for uncompressed assets.
    androidResources.noCompress.add("tflite")

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        // Optional Cloudflare Workers AI credentials (local.properties or environment); see README.
        buildConfigField("String", "CLOUDFLARE_ACCOUNT_ID", buildConfigSecret("CLOUDFLARE_ACCOUNT_ID"))
        buildConfigField("String", "CLOUDFLARE_API_TOKEN", buildConfigSecret("CLOUDFLARE_API_TOKEN"))
    }
}

dependencies {
    implementation(projects.core.common)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mediapipe.tasks.text)
    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)

    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
