plugins {
    alias(libs.plugins.hnfeed.android.library)
    alias(libs.plugins.hnfeed.android.hilt)
}

android {
    namespace = "com.emirkuljanin.hnfeed.core.data"
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.paging.common)

    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.network)

    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.runtime)
}
