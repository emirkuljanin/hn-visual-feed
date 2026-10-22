package com.emirkuljanin.hnfeed.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

private val JAVA_VERSION = JavaVersion.VERSION_17
private val JVM_TARGET = JvmTarget.JVM_17

/** SDK levels, Java target and compiler options shared by every Android module. */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = libs.versionInt("compileSdk")
        defaultConfig.minSdk = libs.versionInt("minSdk")
        compileOptions.sourceCompatibility = JAVA_VERSION
        compileOptions.targetCompatibility = JAVA_VERSION
    }

    // AGP 9 compiles Kotlin itself (built-in Kotlin), so there is no kotlin-android plugin to apply.
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions { configureShared(warningsAsErrors()) }
    }

    addUnitTestDependencies()
}

/** Java target and compiler options for pure Kotlin/JVM modules. */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions { configureShared(warningsAsErrors()) }
    }

    addUnitTestDependencies()
}

/** CI passes `-PwarningsAsErrors=true`; local builds only warn. */
private fun Project.warningsAsErrors(): Provider<Boolean> =
    providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false)

private fun KotlinJvmCompilerOptions.configureShared(warningsAsErrors: Provider<Boolean>) {
    jvmTarget.set(JVM_TARGET)
    allWarningsAsErrors.set(warningsAsErrors)
}

private fun Project.addUnitTestDependencies() {
    dependencies {
        add("testImplementation", kotlin("test-junit"))
        add("testImplementation", libs.library("junit4"))
        add("testImplementation", libs.library("kotlinx-coroutines-test"))
        add("testImplementation", libs.library("turbine"))
    }
}
