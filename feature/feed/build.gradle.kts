plugins {
    alias(libs.plugins.hnfeed.android.feature)
}

android {
    namespace = "com.emirkuljanin.hnfeed.feature.feed"
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.androidx.paging.compose)

    testImplementation(libs.androidx.paging.testing)
}
