plugins {
    alias(libs.plugins.hnfeed.android.library.compose)
}

android {
    namespace = "com.emirkuljanin.hnfeed.core.ui"
}

// Composables shared by features that know the domain model (story thumbnail, metadata, load states).
// `:core:designsystem` stays model-free.
dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)

    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
}
