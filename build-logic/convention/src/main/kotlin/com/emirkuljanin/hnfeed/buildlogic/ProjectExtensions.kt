package com.emirkuljanin.hnfeed.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** The `libs` version catalog. Type-safe accessors are not generated for build-logic sources. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

internal fun VersionCatalog.versionInt(alias: String): Int = findVersion(alias).get().requiredVersion.toInt()

/**
 * A secret for a `buildConfigField`, as a Java string literal: read from the gitignored `local.properties`, else
 * from the environment (CI), else empty. Values are trimmed and escaped, so stray whitespace or quotes cannot break
 * the generated code or the request.
 */
fun Project.buildConfigSecret(name: String): String {
    // Read through providers so local.properties is a declared configuration-cache input.
    val localText = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
    val localProperties = java.util.Properties().apply { localText?.let { load(it.reader()) } }
    // A blank entry (e.g. a template line) must not hide the environment variable.
    val local = localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
    val value = (local ?: providers.environmentVariable(name).orNull).orEmpty().trim()
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
