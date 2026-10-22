plugins {
    alias(libs.plugins.hnfeed.android.library)
}

android {
    namespace = "com.emirkuljanin.hnfeed.core.testing"
}

// Shared test doubles and helpers. Only ever a test/androidTest dependency of other modules.
dependencies {
    api(projects.core.data)
    api(projects.core.model)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
}
