# HN Visual Feed

An Android app that shows Hacker News top stories as a paged feed of image cards. Each story gets a
thumbnail generated from its title, and stories and images are stored locally so the feed works offline.

> Work in progress. Built as a take-home assignment, one reviewed PR at a time.

## Building

Requirements: Android Studio (or any JDK 17+ to start the wrapper) and Android SDK 37. The Gradle daemon runs
on JDK 25 ([`gradle/gradle-daemon-jvm.properties`](gradle/gradle-daemon-jvm.properties)) and Gradle downloads it
if it is missing.

```bash
./gradlew assembleDebug          # build
./gradlew spotlessCheck          # formatting (ktlint + Compose rules); spotlessApply to fix
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew lintDebug              # Android lint
./gradlew connectedDebugAndroidTest  # UI tests on a device/emulator
```

## Modules

| Module | Responsibility |
| --- | --- |
| `:app` | Application, Hilt entry point, top-level wiring |
| `:core:designsystem` | Material 3 theme and shared UI components |
| `:feature:feed` | The story feed screen |
| `build-logic` | Convention plugins shared by all modules (SDK levels, Compose, Hilt, Kotlin options) |

More modules (`:core:network`, `:core:database`, `:core:data`, `:core:imagegen`, `:feature:story`) arrive
with the PRs that need them, so no module exists without code in it.

## Tech stack

Kotlin 2.4, AGP 9 (built-in Kotlin), Gradle 9.8, Jetpack Compose + Material 3, Hilt (KSP), Coroutines.
All dependency versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Decisions & trade-offs

- **Convention plugins over `subprojects {}`**: each module declares what it is (`hnfeed.android.feature`,
  `hnfeed.android.library`, ...) and gets consistent SDK/Kotlin/Compose setup without cross-project
  configuration, which keeps configuration-cache and project isolation happy.
- **minSdk 26**: covers almost all active devices and removes the need for core-library desugaring
  (`java.time` is available natively).
- **Dynamic color with an HN-orange fallback**: Material You on Android 12+, a fixed palette below that and in
  screenshot tests, so screenshots do not depend on the wallpaper.
- **Release build is minified (R8) and debug-signed**: shrinking problems surface early, and reviewers can
  install the release APK without a keystore.
