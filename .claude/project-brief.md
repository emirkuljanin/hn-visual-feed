# Project brief: HN Visual Feed

Shared context for anyone (human or agent) working on or reviewing this repo without the original conversation.
Decisions marked **Agreed** were settled with the user; challenge one only with a concrete defect.

A personal project: a Hacker News reader for the author's own phone. Plans are in
[`docs/roadmap.md`](../docs/roadmap.md); this file records the scope and how the app got here.

## Scope

1. Fetch Top story IDs and details from the official Hacker News Firebase REST API; infinite scrolling.
2. Generate a thumbnail per story title with an AI model (on-device preferred, cloud where on-device is
   impractical; results are cached).
3. Persist stories and images locally (works offline across launches).
4. Jetpack Compose + Material 3; paged cards with image, title, author, score, submission time.
5. Modern Android architecture (UI layer, ViewModel, Repository); Hilt; coroutines with correct dispatchers.
6. Testing: unit tests, plus Roborazzi screenshot tests and Compose UI tests.
7. Fetching, structuring and storing HN **comments** (PR 6).

## Agreed decisions

- **Image generation**: a chain of generators behind the public `ThumbnailGenerator` interface. Cloud first:
  FLUX.1 [schnell] on Cloudflare Workers AI (free 10k neurons/day, about 170 images), with credentials from
  `local.properties`/env (optional; without them everything is on-device). Pollinations was the original plan, but
  its anonymous API answers 402 Payment Required for new prompts since Oct 2026. On-device last: MediaPipe
  Universal Sentence Encoder (6 MB, committed) embeds the title, procedural art is painted from the embedding.
  Two tiers (agreed for 5b): every story gets on-device art at once, then is upgraded to a cloud image as the
  free quota allows, best rank first; at the quota (HTTP 429, code 3036), upgrades pause until it resets
  (midnight UTC). No mandatory
  model download (opt-in on-device diffusion was investigated in PR 8 and dropped).
- **Safety**: title blocklist (words, stems, phrases) plus an abstract "visual metaphor" prompt; Cloudflare also
  screens prompts (HTTP 400, the preview stays). Flagged titles keep the on-device procedural art, which cannot depict anything.
- **Pagination**: Paging 3 `RemoteMediator`, automatic loading. REFRESH fetches the ~500 top IDs once and stores
  a snapshot table `(rank, storyId)`; APPEND slices the stored snapshot and fetches item details in parallel.
  PagingSource = Room join of snapshot + story + thumbnail ordered by rank.
- **Storage**: images as ~512 px WebP files in `filesDir/thumbnails/`, path + status in a separate `thumbnails`
  table (story upserts never clobber images). Evict rows/files for stories no longer in the snapshot after N days.
- **Generation scheduling**: unique WorkManager jobs. Previews: every visible story without a thumbnail, in rank
  order, bounded concurrency; when done they enqueue upgrades. Upgrades: `PENDING` rows in rank order; stop at the
  quota (persisted pause + a separate delayed resume job), or when the provider is unreachable or busy (not counted)
  or at a per-story transient error (counted at most every 15 min, give up after 3), both with a separate delayed
  retry job (as long as the failures have lasted, 15 min to 4 h); cards observe Room and swap placeholder → preview
  → cloud image.
- **Stack**: Kotlin 2.4, AGP 9 (built-in Kotlin), Gradle 9.8, Hilt (KSP), Retrofit + kotlinx.serialization,
  Room 3 (platform SQLite driver), Paging 3, Navigation 3, WorkManager, Coil 3, Roborazzi/Robolectric. Always the latest
  stable versions.
- **Modules**: `:app`, `:core:common` (dispatchers, clock), `:core:model` (JVM), `:core:network`, `:core:database`, `:core:data`, `:core:imagegen`,
  `:core:designsystem`, `:core:ui` (shared model-aware composables, PR 6), `:core:testing`, `:feature:feed`,
  `:feature:story`, `build-logic` convention plugins.
  A module is added by the PR that first needs it.
- **Process**: one PR per task (PRs 1-8 as tickets on a local Gitblit server, then GitHub). Before a PR is
  opened, the `pr-reviewer` agent reviews the
  diff and its findings are fixed or answered. The user then reviews and merges each PR before the next starts.
  PR 9 moved the repo to GitHub (user decision 2026-10-23): PRs, reviews and GitHub Actions CI happen there from
  now on, and Gitblit is retired.

## PR history

The original plan, all merged. New work is planned in [`docs/roadmap.md`](../docs/roadmap.md).

1. Project skeleton (build-logic, catalog, Hilt, theme, placeholder feed). *Merged.*
2. Network + database + models (MockWebServer, in-memory Room tests). *Merged.*
3. Paging: RemoteMediator, repository, snapshot logic (tests), minimal list wired into the app. *Merged.*
4. Feed UI: cards with placeholders, pull-to-refresh, load/error/empty states with retry, scroll to top after a
   refresh, ViewModel tests, Roborazzi screenshots, Compose stability config for `:core:model`. *Merged.*
5. Image generation, split in two (both merged):
   - 5a. `:core:imagegen`: generator chain, Cloudflare + on-device generators, safety filter. *Merged.*
   - 5b. Pipeline: `thumbnails` table (first Room migration), WorkManager job, file storage and cleanup, cards show
     images, two-tier preview/upgrade with a quota pause, app wiring (exclude MediaPipe's unused textgenai `.so`,
     ABI filters, R8 check of the embedder in a release build). *Merged.*
6. Story detail + comments (adjacency list in Room with stub rows, lazy expansion by level, Navigation 3 in
   `:app`, shared composables moved to `:core:ui`). *Merged.*
7. Polish: offline awareness with automatic retry, accessibility (TalkBack, largest font), shared thumbnail
   transition, app-level Hilt UI tests on Robolectric, README overhaul. *Merged.*
8. Investigated and dropped (user decision 2026-10-23): opt-in on-device diffusion. MediaPipe Image
   Generator ran on the user's Pixel 7 Pro (Tensor G2) after working around a mis-built release (full-protobuf
   call), a MediaPipe version clash and a native-runtime clash, then produced only noise; the community SD 1.5
   conversion was proven byte-identical to the official weights, which points to the GPU engine/driver. The remaining path
   (stable-diffusion.cpp on CPU with SDXS, native build) was not worth the risk. PR 8 ships the
   write-up only: `docs/on-device-diffusion.md`. *Merged.*
9. Move to GitHub with GitHub Actions CI: a public repository; checks on every PR and push to master (format, lint,
   JVM/Robolectric tests, screenshots, both builds), instrumented tests on an emulator, the release APK as an
   artifact and on tagged GitHub Releases. CI has no Cloudflare secrets (a public APK must not carry the token).
   *Merged* (GitHub PR #1, with a status fix in #2). `v1.0.0` released from here. Gitblit retired; all PRs on GitHub
   from here on.
10. Two code audits, not in the original plan. GitHub PR #3: feed scroll position (Paging placeholders),
    Cloudflare error classification, comment retry state, a flaky paging test. GitHub PR #4: wide layouts, cloud
    upgrade retries without WorkManager backoff, test hardening. *Merged.* Neither is in a release yet.

## Environment

Windows 10, Git Bash. Android Studio JBR (JDK 25) at `C:\Program Files\Android\Android Studio\jbr`.
Emulator AVD `Pixel_7_Pro_34`. Package `com.emirkuljanin.hnfeed`.
