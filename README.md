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
./gradlew testDebugUnitTest      # JVM unit tests (runs screenshot tests without comparing)
./gradlew verifyRoborazziDebug   # screenshot tests compared with src/test/screenshots (recordRoborazziDebug to update)
./gradlew lintDebug              # Android lint
./gradlew connectedDebugAndroidTest  # UI tests on a device/emulator
```

## Modules

| Module | Responsibility |
| --- | --- |
| `:app` | Application, Hilt entry point, top-level wiring |
| `:core:common` | Cross-cutting utilities: injectable coroutine dispatchers and `Clock` |
| `:core:data` | Repositories and the Paging `RemoteMediator`: decides when to fetch and writes results to Room |
| `:core:designsystem` | Material 3 theme and shared UI components |
| `:core:model` | Pure Kotlin domain types (`Story`), shared by every layer |
| `:core:network` | Hacker News API client (Retrofit + kotlinx.serialization) |
| `:core:database` | Room database: cached stories and the top-stories snapshot |
| `:core:testing` | Test-only: shared fakes, test data with a fixed "now", `MainDispatcherRule` |
| `:feature:feed` | The story feed screen: cards, load states, pull-to-refresh |
| `build-logic` | Convention plugins (SDK levels, Compose, Hilt, Room, Roborazzi, Kotlin options) |

More modules (`:core:imagegen`, `:feature:story`) arrive with the PRs that need them, so no module exists without
code in it.

## Tech stack

Kotlin 2.4, AGP 9 (built-in Kotlin), Gradle 9.8, Jetpack Compose + Material 3, Hilt (KSP), Coroutines,
Retrofit 3 + OkHttp 5 + kotlinx.serialization, Room 3, Paging 3. Tests: JUnit4, kotlinx-coroutines-test,
MockWebServer, Robolectric, paging-testing, Compose UI tests, Roborazzi. All dependency versions live in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml).

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

### Paging

- **Room is the paging source, the network only fills it**: the feed is a Room `PagingSource`; the
  `TopStoriesRemoteMediator` fetches when Paging asks and writes into Room. The UI never sees network data directly,
  which is what makes the feed work offline.
- **REFRESH** fetches the ranking and the first page, then replaces the snapshot and writes the page in one
  transaction (network first, so a failed refresh leaves the old feed). An empty ranking is treated as an error
  rather than wiping the feed. **APPEND** walks the stored snapshot without re-fetching the ranking. **PREPEND**
  never happens.
- **Skip the launch refresh while the snapshot is fresh (15 min)**: otherwise every new Pager (cold start, return
  after process death) would re-rank the feed and throw away the user's scroll position. The snapshot's age is the
  earliest `fetched_at` among loaded rows, i.e. when its first page was written. Pull-to-refresh always refreshes.
- **Page size 20 = one network batch**: each page is one parallel batch of item requests. `initialLoadSize`
  matches it (the mediator fetches one page per refresh) and placeholders are off: 500 empty cards help nobody.
- **Error contract**: the network layer throws only `IOException`s (`HnApiException` for unusable responses), so
  Retrofit and kotlinx.serialization types never leave `:core:network`. The mediator turns those and SQLite
  failures (e.g. a full disk) into `MediatorResult.Error`, which the UI gets as a load state instead of a crash.
- **APPEND re-checks its page inside the transaction**: if another Pager refreshed while it was fetching, it writes
  nothing, so ids are never marked loaded at arbitrary new ranks. Each Pager gets its own mediator instance.
- **Tests**: the mediator and repository are tested against a real in-memory Room database and a hand-written fake
  network; repository tests run the whole Paging pipeline with `paging-testing`'s `asSnapshot`. `HnDatabase` is
  public only so these cross-module tests can build an in-memory instance.

### Feed UI

- **What the screen shows** is decided by whether stories are on screen, then by Paging's load states:

  | Situation | Shown |
  | --- | --- |
  | Stories cached, launch refresh running | the cached list, pull-to-refresh indicator, spinner footer |
  | Stories cached, refresh failed (offline) | the cached list, "Couldn't refresh" banner and a retry footer |
  | Nothing cached, first load running | full-screen spinner |
  | Nothing cached, first load failed | full-screen error with Retry |
  | Loaded, nothing to show | empty state with a Refresh button |
  | Scrolled to the end | spinner while the next page loads, retry footer if it fails |

  Until a launch refresh succeeds Paging holds appends back (they report `NotLoading`, not an error), so the footer
  also reflects the refresh state; otherwise an offline list would simply end with the retry far above.
- **Scroll to top after a remote refresh**: items are keyed by story id (stable identity, item reuse, TalkBack focus,
  thumbnails swapping in later), so on its own the list would follow the first visible story to its new rank. Once
  a refresh has landed the list jumps to the new top. It waits for the combined refresh state, which only settles
  after Room has re-emitted, so the jump is not undone by the new page arriving a frame later. A refresh skipped
  because the snapshot is fresh leaves the scroll position alone.
- **Pull-to-refresh** shows the mediator refresh (network), not the combined state, so it does not flicker on
  local Room reloads.
- **Safe links**: only `http(s)` URLs are kept from the API; opening one is guarded because a device without a
  browser would otherwise crash. Text posts open their HN discussion (until the story detail screen exists).
- **Time**: "2 hours ago" uses the platform's localized relative time against `LocalClock`, which `MainActivity`
  sets to the same injected `Clock` the data layer uses; tests fix it. Small clock skew never shows "In 2 minutes".
- **Screenshot tests** (Roborazzi on Robolectric, `src/test/screenshots`) cover every state in light and dark with
  the static palette, a fixed clock and the UTC time zone. They were recorded on Windows; CI on Linux may need
  re-recording (or a small diff threshold) because of font anti-aliasing.
- **Robolectric on JDK 25**: the API 36 runtime needs `--add-exports java.base/jdk.internal.access`, and Compose's
  UI test library pulls Espresso 3.5 which breaks on it, so Espresso 3.7 is pinned. Both live in
  `hnfeed.android.roborazzi`.
