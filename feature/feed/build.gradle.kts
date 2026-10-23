plugins {
    alias(libs.plugins.hnfeed.android.feature)
    alias(libs.plugins.hnfeed.android.roborazzi)
}

android {
    namespace = "com.emirkuljanin.hnfeed.feature.feed"
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.compose)

    testImplementation(libs.androidx.paging.testing)
}
