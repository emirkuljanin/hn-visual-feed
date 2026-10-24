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
GitHub Actions (`.github/workflows/ci.yml`) runs the same checks with `-PwarningsAsErrors=true` and fails on any
lint issue; keep the two in sync. CI has no secrets: never add the Cloudflare token to a workflow.

## Architecture

Layers: **UI (Compose) → ViewModel → Repository → data sources (network, database, image generation)**.

Module dependency direction (arrows = "may depend on"); a module is added by the first PR that needs it:

```
:app                          ──> :feature:*, :core:ui, :core:designsystem
:feature:*                    ──> :core:data, :core:ui, :core:designsystem
:core:data                    ──> :core:network, :core:database, :core:imagegen, :core:model, :core:common
:core:database                ──> :core:model, :core:common
:core:ui                      ──> :core:designsystem, :core:model
:core:network, :core:imagegen ──> :core:common
:core:designsystem, :core:common, :core:model ──> nothing (`:core:model` is pure Kotlin)
```

The list is what each module declares today (production code; `:core:testing` is test-only, see below). Types a
dependency exposes through `api` may be used directly (e.g. `:core:model`, which `:core:data` and `:core:ui`
expose to features and `:app`). Any Android module may add `:core:common` (injectable dispatchers, `Clock` and
other small utilities).
`:core:imagegen` exposes only `ThumbnailGenerator` (an interface) and its request/result types; generators are internal.
`:core:ui` holds composables several features share that know the model (thumbnail, story metadata, load states,
`PreviewTheme`); `:core:designsystem` stays model-free. Features get both through `hnfeed.android.feature`.
`:core:testing` (shared fakes, test data, `MainDispatcherRule`) is only ever a test dependency; feature modules get
it automatically through `hnfeed.android.feature`.
- **Navigation** (Navigation 3) lives in `:app` (`navigation/HnFeedNavDisplay.kt`): keys and the entry provider.
  Features never navigate themselves; their `XxxRoute` reports clicks through callbacks (ids, not models).

- `:core:model` is pure Kotlin (no Android). Network DTOs and Room entities stay inside their modules and are
  mapped to model types at the boundary; they never leak into features.
- Features talk only to repositories in `:core:data`, never to Retrofit or Room directly.
- **Room is the single source of truth.** The network and the image generator write into the database; the UI
  only observes database-backed `Flow`s / `PagingData`.
- **Threading**: classes that do blocking or CPU-heavy work are main-safe and switch dispatchers themselves, using an
  injected `@IoDispatcher` (network, disk, database) or `@DefaultDispatcher` (image work, inference)
  (never a hard-coded `Dispatchers.IO`/`Default`), so tests can substitute a test dispatcher.
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
  has `@Preview`s in `PreviewTheme` (fixed clock, `dynamicColor = false`, drawn thumbnails); tests use it too.
  Strings go in resources, quantities as plurals. A ViewModel that needs a navigation argument takes it by
  assisted injection (`hiltViewModel<VM, VM.Factory>(creationCallback = …)`).
- Compose stability: [`compose-stability.conf`](compose-stability.conf) marks `java.time.Instant` and
  `core.model.*` (direct package only, not subpackages) as stable. That is a promise: model classes must stay
  deeply immutable (`val`s of immutable types; collections as `List` that is never mutated after creation).
- Paging lists: key items by id (`itemKey`) and use `contentType`; derive loading/error/empty UI from
  `LazyPagingItems.loadState`, not from copies in a UiState.
- ViewModels expose `StateFlow<UiState>` (plus `Flow<PagingData<…>>` for lists); no one-shot event channels.
- Tests: JUnit4 + `kotlinx-coroutines-test` + Turbine; prefer hand-written fakes over mocks. Network code is tested
  against MockWebServer, DAOs and the data layer against an in-memory Room database under Robolectric, Paging
  end to end with `paging-testing`'s `asSnapshot`, Compose UI with Compose UI tests on Robolectric, and flows
  across screens with app-level tests in `app/src/test` (Hilt, `@TestInstallIn` fakes). New behaviour ships with
  tests in the same PR. Screenshot tests use Roborazzi (`hnfeed.android.roborazzi`); re-record
  baselines only for intended changes and say so in the PR.
- Formatting is enforced by Spotless (ktlint `android_studio` style + Compose rules); don't hand-format.

## Workflow

- One GitHub PR per planned task, reviewed and merged by the user before the next one starts; CI must be green.
  (PRs 1-8 were reviewed as tickets on a local Gitblit server, hence their "Merged #n" commits by `admin`.)
- Before opening a PR: run `verify-build`, then have the `pr-reviewer` agent review the branch and handle its
  findings. Details in the `open-pr` skill.
- Skills live in `.claude/skills/`: project workflows (`verify-build`, `open-pr`), official Android skills
  (see `THIRD_PARTY.md`), and feature skills added by the PR that builds the feature.
