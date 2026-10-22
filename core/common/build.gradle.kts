plugins {
    alias(libs.plugins.hnfeed.android.library)
    alias(libs.plugins.hnfeed.android.hilt)
}

android {
    namespace = "com.emirkuljanin.hnfeed.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
