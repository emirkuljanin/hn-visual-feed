---
name: verify-build
description: Build, format-check, lint and test the HN Visual Feed Android project on this Windows machine (unit, screenshot and emulator UI tests). Use before opening or updating any PR, or when asked to check that the project builds or tests pass.
---

# Verify the build

All commands run from the repo root in the Bash tool (Git Bash). Gradle needs a JDK 17+; the shell's default
`java` is JDK 8, so always export Android Studio's bundled JDK first:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```

`gradle/gradle-daemon-jvm.properties` pins the daemon to JDK 25 (auto-provisioned elsewhere via foojay).
`local.properties` (gitignored) points at the SDK in `%LOCALAPPDATA%\Android\Sdk`.

## Checks, cheapest first

1. Format: `./gradlew spotlessApply` then `./gradlew spotlessCheck` (ktlint + Compose rules, see `.editorconfig`).
2. Compile: `./gradlew assembleDebug`. Run `assembleRelease` too when touching R8, serialization, Hilt or reflection.
3. Unit tests: `./gradlew testDebugUnitTest` (includes Robolectric/Roborazzi screenshot tests once they exist).
   - Screenshot baselines: `./gradlew recordRoborazziDebug` to (re)record, `verifyRoborazziDebug` to compare.
     Only re-record when the UI change is intended, and say so in the PR.
4. Lint: `./gradlew lintDebug`. Must end with "No issues found" in `app/build/reports/lint-results-debug.txt`.
5. UI tests on the emulator: `./gradlew connectedDebugAndroidTest`.

Redirect long output to a file in `$TEMP` and grep it (`^e:|^w:|FAIL|BUILD`), rather than dumping it all.
Treat new Kotlin warnings (`^w:`) as things to fix; CI builds with `-PwarningsAsErrors=true`.

## Emulator

- AVD: `Pixel_7_Pro_34`. Start it in the background:
  `"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Pixel_7_Pro_34 -no-snapshot-save -no-boot-anim`
- Ready when `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" shell getprop sys.boot_completed` prints `1`.
- Screenshot for a PR: install `app/build/outputs/apk/debug/app-debug.apk`, `am start -W -n
  com.emirkuljanin.hnfeed/.MainActivity`, wait ~2 s (the launch fade makes earlier captures look washed out),
  then `adb exec-out screencap -p > file.png`.

## Reporting

State exactly which of the steps above ran and their result. If one was skipped (e.g. emulator unavailable),
say so; never report "all green" for checks that did not run.
