plugins {
    alias(libs.plugins.hnfeed.android.library)
    alias(libs.plugins.hnfeed.android.room)
    alias(libs.plugins.hnfeed.android.hilt)
}

android {
    namespace = "com.emirkuljanin.hnfeed.core.database"
}

dependencies {
    api(projects.core.model)
    api(libs.room.paging)

    implementation(projects.core.common)
    implementation(libs.androidx.sqlite.framework)

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing)
}
