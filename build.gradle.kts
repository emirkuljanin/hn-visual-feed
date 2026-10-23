plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    val composeRules = "io.nlopez.compose.rules:ktlint:${libs.versions.composeRules.get()}"

    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
            .setEditorConfigPath(rootProject.file(".editorconfig"))
            .customRuleSets(listOf(composeRules))
            // Allowed CompositionLocals are environment data like LocalConfiguration: LocalClock (every time label
            // reads it, tests override it) and the two shared-element scopes (set once by the app's navigation).
            // (Set here because Spotless does not pass custom rule-set properties through from .editorconfig.)
            .editorConfigOverride(
                mapOf(
                    "compose_allowed_composition_locals" to
                        "LocalClock,LocalSharedTransitionScope,LocalNavAnimatedVisibilityScope",
                ),
            )
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).setEditorConfigPath(rootProject.file(".editorconfig"))
    }
}
