# Project brief: HN Visual Feed

Shared context for anyone (human or agent) working on or reviewing this repo without the original conversation.
Decisions marked **Agreed** were settled with the user; challenge one only with a concrete defect.

## The assignment

Take-home for a **Senior Android Developer** role at Yttr AS. Target effort 4–6 h; the code is mainly a
starting point for a technical interview about code, architecture and technical choices (Thu 2026-10-29).
Code must be shared by Wed 2026-10-28.

Requirements:
1. Fetch Top story IDs and details from the official Hacker News Firebase REST API; infinite scrolling.
2. Generate a thumbnail per story title with an AI model (on-device preferred; the hiring manager said a cloud
   generator is acceptable if on-device is impractical, and to *think about caching results*).
3. Persist stories and images locally (works offline across launches).
4. Jetpack Compose + Material 3; paged cards with image, title, author, score, submission time.
5. Modern Android architecture (UI layer, ViewModel, Repository); Hilt; coroutines with correct dispatchers.
6. Testing (spec just says "Testing"): unit tests, plus Roborazzi screenshot tests and Compose UI tests.
Interview may also cover fetching/structuring/storing HN **comments**; we will implement them.

## Agreed decisions

- **Image generation**: `ImageGenerator` interface. Cloud text-to-image (free no-key provider, e.g.
  Pollinations; verify) is primary. On-device fallback: a small MediaPipe text embedder bundled in the APK turns
  the title into a vector that drives procedural art. No mandatory model download (opt-in diffusion: PR 8).
- **Safety**: SFW filter on titles (blocklist + safe prompt template, provider safe mode). Flagged titles go to
  the on-device generator, which cannot produce unsafe imagery.
- **Pagination**: Paging 3 `RemoteMediator`, automatic loading. REFRESH fetches the ~500 top IDs once and stores
  a snapshot table `(rank, storyId)`; APPEND slices the stored snapshot and fetches item details in parallel.
  PagingSource = Room join of snapshot + story + thumbnail ordered by rank.
- **Storage**: images as ~512 px WebP files in `filesDir/thumbnails/`, path + status in a separate `thumbnail`
  table (story upserts never clobber images). Evict rows/files for stories no longer in the snapshot after N days.
- **Generation scheduling**: rows inserted as `PENDING`; one unique WorkManager job drains PENDING in rank order
  with bounded concurrency, retry with backoff; cards observe status via Room and swap placeholder → image.
- **Stack**: Kotlin 2.4, AGP 9 (built-in Kotlin), Gradle 9.8, Hilt (KSP), Retrofit + kotlinx.serialization,
  Room, Paging 3, WorkManager, Coil 3, Roborazzi/Robolectric. Always the latest stable versions.
- **Modules**: `:app`, `:core:model` (JVM), `:core:network`, `:core:database`, `:core:data`, `:core:imagegen`,
  `:core:designsystem`, `:core:testing`, `:feature:feed`, `:feature:story`, `build-logic` convention plugins.
  A module is added by the PR that first needs it.
- **Process**: one PR (Gitblit ticket) per task. Before a ticket is opened, the `pr-reviewer` agent reviews the
  diff and its findings are fixed or answered. The user then reviews and merges each PR before the next starts.
  Finally the repo moves to GitHub with GitHub Actions CI.

## PR plan

1. Project skeleton (build-logic, catalog, Hilt, theme, placeholder feed). *Done, in review.*
2. Network + database + models (MockWebServer, in-memory Room tests).
3. Paging: RemoteMediator, repository, snapshot logic (tests).
4. Feed UI: cards with placeholders, pull-to-refresh, ViewModel tests, Roborazzi screenshots.
5. Image generation: interface, cloud + on-device generators, safety filter, WorkManager, file storage, cleanup.
6. Story detail + comments (adjacency list in Room, lazy expansion).
7. Polish: offline/error states, Compose UI tests, README decisions; later GitHub + Actions.
8. Stretch: opt-in on-device diffusion. Settings toggle with warning, permission-free requirements check
   (RAM, arm64, SoC, free storage, Vulkan, thermal) and a one-step benchmark, Wi-Fi/charging-only model download,
   `DiffusionImageGenerator` behind the existing interface. Testable on the user's physical Pixel 7 Pro (Tensor G2,
   CPU/GPU path, no Qualcomm NPU). If time runs out: a commented skeleton. PR 5 must leave the generator-selection
   strategy ready for it.

## Environment

Windows 10, Git Bash. Android Studio JBR (JDK 25) at `C:\Program Files\Android\Android Studio\jbr`.
Emulator AVD `Pixel_7_Pro_34`. Package `com.emirkuljanin.hnfeed`.
