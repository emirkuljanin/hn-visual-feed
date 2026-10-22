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
| `:core:common` | Cross-cutting utilities, currently the injectable coroutine dispatchers |
| `:core:designsystem` | Material 3 theme and shared UI components |
| `:core:model` | Pure Kotlin domain types (`Story`), shared by every layer |
| `:core:network` | Hacker News API client (Retrofit + kotlinx.serialization) |
| `:core:database` | Room database: cached stories and the top-stories snapshot |
| `:feature:feed` | The story feed screen |
| `build-logic` | Convention plugins shared by all modules (SDK levels, Compose, Hilt, Room, Kotlin options) |

More modules (`:core:data`, `:core:imagegen`, `:feature:story`) arrive with the PRs that need them, so no module
exists without code in it.

## Tech stack

Kotlin 2.4, AGP 9 (built-in Kotlin), Gradle 9.8, Jetpack Compose + Material 3, Hilt (KSP), Coroutines,
Retrofit 3 + OkHttp 5 + kotlinx.serialization, Room 3. Tests: JUnit4, kotlinx-coroutines-test, MockWebServer,
Robolectric. All dependency versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

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

### Data

- **Top-stories snapshot table**: the API returns up to 500 ranked ids that reorder constantly. Refresh stores
  them once as `top_stories(rank, story_id, is_loaded)`, and paging walks that snapshot, so pages never shift
  under the user (no duplicates or skipped stories). No foreign key to `stories`: entries exist before their
  details are fetched.
- **The feed is the loaded top N**: paging loads entries strictly in rank order and marks them `is_loaded`
  (deleted items too, so paging always moves forward), and the feed query shows only loaded entries. The visible
  list is therefore always an unbroken prefix of the snapshot, and new pages only appear below it, never above.
- **Stories outlive the snapshot**: `stories` is a cache keyed by id, so the last feed is still there offline;
  `fetched_at` is for evicting old rows. Cached stories from an older snapshot stay hidden until paging reloads
  them, so the feed never shows stale scores out of order. When a reload finds an item missing, deleted or dead,
  its cached row is deleted in the same transaction, so a killed story cannot resurface.
- **Atomic multi-table writes**: `DatabaseTransactionRunner` wraps Room's write transaction, so a refresh replaces
  the snapshot and writes the first page together (the UI never sees an empty or half-written feed) and a failure
  rolls everything back. It is an interface so the data layer can be tested with a fake.
- **Room 3 with the platform SQLite driver**: Room 3 needs an explicit driver. The platform one adds no native
  library to the APK and is what Robolectric runs the DAO tests on; a bundled SQLite would only buy a fixed SQLite
  version.
  Queries run on the injected IO dispatcher (`setQueryCoroutineContext`).
- **Injected dispatchers**: `:core:common` provides `@IoDispatcher`. The network data source switches to it
  explicitly: Retrofit creates each call on the calling thread (on first use that builds the lazily injected
  OkHttpClient), and the data source decodes items itself.
- **One request per item, in parallel**: the API has no batch endpoint. Items are fetched concurrently with
  structured concurrency (one failure cancels the rest and fails the page, so paging can retry it). OkHttp's
  per-host limit is raised from 5 to 10 so a 20-item page is not mostly queued.
- **`null` items**: the API answers unknown ids with JSON `null`. Retrofit's converter only sees the Java type, so
  it cannot decode that into a nullable Kotlin type; the item endpoint returns a `JsonElement` and the data source
  maps `JsonNull` to "skip".
- **No destructive migration fallback**: schemas are exported to `core/database/schemas` and every version bump
  ships a migration. A forgotten migration then fails in testing instead of silently wiping the offline cache.
- **DAO tests on the JVM with Robolectric**: real SQLite and Room, without an emulator, so they run with the
  other unit tests and in CI.
