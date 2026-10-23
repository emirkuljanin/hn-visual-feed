plugins {
    alias(libs.plugins.hnfeed.android.application.compose)
    alias(libs.plugins.hnfeed.android.hilt)
    alias(libs.plugins.hnfeed.android.roborazzi)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.emirkuljanin.hnfeed"

    defaultConfig {
        applicationId = "com.emirkuljanin.hnfeed"
        versionCode = 1
        versionName = "0.1.0"
        // MediaPipe ships native code for four ABIs; phones and the emulator images we target need only these two.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    androidResources {
        // Lets MediaPipe memory-map the text embedder model straight from the APK.
        noCompress += "tflite"
    }

    packaging {
        jniLibs {
            // tasks-text also bundles the on-device LLM runtime (tens of MB), which the embedder does not use.
            excludes += "**/libmediapipe_tasks_textgenai_jni.so"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug-signed so reviewers can install a release build without a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.feature.feed)
    implementation(projects.feature.story)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.work.runtime)

    // App-level UI tests on Robolectric: the real app with Hilt, repositories swapped for fakes.
    testImplementation(projects.core.common)
    testImplementation(projects.core.testing)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
