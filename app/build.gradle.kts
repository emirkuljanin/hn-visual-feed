plugins {
    alias(libs.plugins.hnfeed.android.application.compose)
    alias(libs.plugins.hnfeed.android.hilt)
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
    implementation(projects.feature.feed)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
