plugins {
    alias(libs.plugins.hnfeed.android.library.compose)
}

android {
    namespace = "com.emirkuljanin.hnfeed.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
}
