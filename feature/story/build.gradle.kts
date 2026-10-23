plugins {
    alias(libs.plugins.hnfeed.android.feature)
    alias(libs.plugins.hnfeed.android.roborazzi)
}

android {
    namespace = "com.emirkuljanin.hnfeed.feature.story"
}

dependencies {
    implementation(projects.core.data)
}
