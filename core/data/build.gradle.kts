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
    implementation(projects.core.imagegen)
    implementation(projects.core.network)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.runtime)
}
