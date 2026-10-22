plugins {
    alias(libs.plugins.hnfeed.android.library)
    alias(libs.plugins.hnfeed.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.emirkuljanin.hnfeed.core.network"
}

dependencies {
    implementation(projects.core.common)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    testImplementation(libs.okhttp.mockwebserver)
}
