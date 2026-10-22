# AGENTS.md

Guidance for AI coding agents (and humans) working in this repository. Keep it short and current: if a rule
here stops matching the code, fix whichever one is wrong in the same PR.

**HN Visual Feed**: an Android app that shows Hacker News top stories as an offline-capable, paged feed of
cards, each with an AI-generated thumbnail. The assignment, agreed decisions and PR plan are in
[`.claude/project-brief.md`](.claude/project-brief.md). Read it before making design decisions.

## Commands

Use a JDK 17+ to launch Gradle (on the author's Windows machine:
`export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`). The daemon itself runs on JDK 25.

| Purpose | Command |
| --- | --- |
| Format / check format | `./gradlew spotlessApply` / `./gradlew spotlessCheck` |
| Build | `./gradlew assembleDebug` (and `assembleRelease` when touching R8/serialization/DI) |
| Unit tests | `./gradlew testDebugUnitTest` (also runs screenshot tests, but does not compare images) |
| Screenshot tests | `./gradlew verifyRoborazziDebug` compares with `*/src/test/screenshots`; `recordRoborazziDebug` re-records |
| Lint | `./gradlew lintDebug` (must report no issues) |
| UI tests (emulator) | `./gradlew connectedDebugAndroidTest` |

Never run `clean` to "fix" a build; find the cause. The `verify-build` skill has the full procedure.

## Architecture

Layers: **UI (Compose) → ViewModel → Repository → data sources (network, database, image generation)**.

Module dependency direction (arrows = "may depend on"); a module is added by the first PR that needs it:

```
:app ──> :feature:* ──> :core:data ──> :core:network, :core:database, :core:imagegen ──> :core:model
             └────────> :core:designsystem
```

Any Android module may depend on `:core:common` (injectable dispatchers and other small utilities).
`:core:testing` (shared fakes, test data, `MainDispatcherRule`) is only ever a test dependency; feature modules get
it automatically through `hnfeed.android.feature`.

- `:core:model` is pure Kotlin (no Android). Network DTOs and Room entities stay inside their modules and are
  mapped to model types at the boundary; they never leak into features.
- Features talk only to repositories in `:core:data`, never to Retrofit or Room directly.
- **Room is the single source of truth.** The network and the image generator write into the database; the UI
  only observes database-backed `Flow`s / `PagingData`.
- **Threading**: classes that do blocking work are main-safe and switch dispatchers themselves, using an injected
  `CoroutineDispatcher` (never a hard-coded `Dispatchers.IO`), so tests can substitute a test dispatcher.
  No `GlobalScope`, no `runBlocking` outside tests. Long-lived background work goes through WorkManager.
- **Time**: inject `java.time.Clock` (provided by `:core:common`); never call `Instant.now()` directly. Compose code
  reads it from `LocalClock`, which `MainActivity` sets to the injected clock and tests set to a fixed one.
- **Errors**: `:core:network` throws only `IOException`s (`HnApiException` for unusable responses); Retrofit and
  kotlinx.serialization types never leave it. Paging code turns failures into `LoadState.Error` instead of throwing.
- **DI**: Hilt everywhere; bindings live in a `di` package of the module that owns the implementation, and
  modules expose interfaces where a fake is useful in tests.

## Conventions

- Package root `com.emirkuljanin.hnfeed`, then the module path (e.g. `...core.designsystem`, `...feature.feed`).
- Build setup lives in `build-logic` convention plugins (`hnfeed.android.feature`, `hnfeed.android.library`, ...).
  Module build files only declare their plugin, namespace and dependencies.
- All versions in `gradle/libs.versions.toml`, always the **latest stable** release (check before adding).
  KSP only, never kapt. AGP 9 built-in Kotlin: do not apply `org.jetbrains.kotlin.android`.
- Compose: a `XxxRoute` composable gets the ViewModel (`hiltViewModel()`) and collects state with
  `collectAsStateWithLifecycle()`; it passes plain state and lambdas to a stateless `XxxScreen`. Every screen
  has `@Preview`s with a fixed clock and `dynamicColor = false`. Strings go in resources, quantities as plurals.
- Compose stability: [`compose-stability.conf`](compose-stability.conf) marks `java.time.Instant` and
  `core.model.*` (direct package only, not subpackages) as stable. That is a promise: model classes must stay
  deeply immutable (`val`s of immutable types; collections as `List` that is never mutated after creation).
- Paging lists: key items by id (`itemKey`) and use `contentType`; derive loading/error/empty UI from
  `LazyPagingItems.loadState`, not from copies in a UiState.
- ViewModels expose `StateFlow<UiState>` (plus `Flow<PagingData<…>>` for lists); no one-shot event channels.
- Tests: JUnit4 + `kotlinx-coroutines-test` + Turbine; prefer hand-written fakes over mocks. Network code is tested
  against MockWebServer, DAOs and the data layer against an in-memory Room database under Robolectric, Paging
  end to end with `paging-testing`'s `asSnapshot`, Compose UI with Compose UI tests on Robolectric. New behaviour
  ships with tests in the same PR. Screenshot tests use Roborazzi (`hnfeed.android.roborazzi`); re-record
  baselines only for intended changes and say so in the PR.
- Formatting is enforced by Spotless (ktlint `android_studio` style + Compose rules); don't hand-format.

## Workflow

- One PR (a Gitblit ticket) per planned task, reviewed and merged by the user before the next one starts.
- Before opening a ticket: run `verify-build`, then have the `pr-reviewer` agent review the branch and handle its
  findings. Details in the `open-pr` skill.
- Skills live in `.claude/skills/`: project workflows (`verify-build`, `open-pr`), official Android skills
  (see `THIRD_PARTY.md`), and feature skills added by the PR that builds the feature.
