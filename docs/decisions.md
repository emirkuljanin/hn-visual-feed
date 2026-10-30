# Decisions and trade-offs

Why the code is the way it is, by area. The overview is in the [README](../README.md); the rules for changing the
code are in [AGENTS.md](../AGENTS.md).

- **Convention plugins over `subprojects {}`**: each module declares what it is (`hnfeed.android.feature`,
  `hnfeed.android.library`, ...) and gets consistent SDK/Kotlin/Compose setup without cross-project
  configuration, which keeps configuration-cache and project isolation happy.
- **minSdk 26**: covers almost all active devices and removes the need for core-library desugaring
  (`java.time` is available natively).
- **Dynamic color with an HN-orange fallback**: Material You on Android 12+, a fixed palette below that and in
  screenshot tests, so screenshots do not depend on the wallpaper.
- **Release build is minified (R8) and debug-signed**: shrinking problems surface early, and anyone can
  install the release APK without a keystore.

## Data

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
- **Injected dispatchers**: `:core:common` provides `@IoDispatcher` and `@DefaultDispatcher`. The network data source
  switches to the IO one explicitly: Retrofit creates each call on the calling thread (on first use that builds the lazily injected
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

## Paging

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
  matches it (the mediator fetches one page per refresh).
- **Placeholders are on, so list indices are ranks**: Room reloads around the scroll anchor after every write
  (thumbnails, an opened story's refresh), and an index restored when the feed comes back (from a story, or after
  process death) must still point at the same story. Without placeholders the reloaded list starts about ten items
  above the anchor and the restored index lands that far down. Placeholders only span stories already loaded into
  Room (the query's row count), not the ~500-id snapshot; one shows as a blank card until its page is read.
- **Error contract**: the network layer throws only `IOException`s (`HnApiException` for unusable responses), so
  Retrofit and kotlinx.serialization types never leave `:core:network`. The mediator turns those and SQLite
  failures (e.g. a full disk) into `MediatorResult.Error`, which the UI gets as a load state instead of a crash.
- **APPEND re-checks its page inside the transaction**: if another Pager refreshed while it was fetching, it writes
  nothing, so ids are never marked loaded at arbitrary new ranks. Each Pager gets its own mediator instance.
- **Tests**: the mediator and repository are tested against a real in-memory Room database and a hand-written fake
  network; repository tests run the whole Paging pipeline with `paging-testing`'s `asSnapshot`. `HnDatabase` is
  public only so these cross-module tests can build an in-memory instance.

## Feed UI

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
- **Wide layouts**: from 600 dp of width (a phone in landscape, a tablet) a card's thumbnail takes the start third
  of the card and the text sits beside it; otherwise a full-width 16:9 image would fill the screen and push the title
  below the fold. The same happens in a short window, where the full-width image would be taller than half the
  window: a compact phone in landscape (360 x 640 dp, whose cards are under 600 dp once the camera cutout is taken
  off) or split screen. Phones in portrait stay as they were (their image is about a quarter of the window). The
  story header does the same, so a story opens on its title. Both use `StoryThumbnailLayout` in
  `:core:ui`, which decides while measuring (a custom `Layout`): both shapes are one composition and no list item pays
  for a `BoxWithConstraints` subcomposition; placeholders use the same layout and keep their height, and the
  thumbnail is 16:9 in both shapes, so the shared transition is a plain resize. Still one `LazyColumn`, so keys,
  placeholders and the restored scroll position work as in portrait.
- **Safe links**: only `http(s)` URLs are kept from the API; opening one is guarded because a device without a
  browser would otherwise crash. A card opens the story screen; the article opens from there.
- **Time**: "2 hours ago" uses the platform's localized relative time against `LocalClock`, which `MainActivity`
  sets to the same injected `Clock` the data layer uses; tests fix it. Small clock skew never shows "In 2 minutes".
- **Screenshot tests** (Roborazzi on Robolectric, `src/test/screenshots`) cover every state in light and dark with
  the static palette, a fixed clock and the UTC time zone. They were recorded on Windows and verified unchanged on
  the Linux CI runner.
- **Robolectric on JDK 25**: the API 36 runtime needs `--add-exports java.base/jdk.internal.access`, and Compose's
  UI test library pulls Espresso 3.5 which breaks on it, so Espresso 3.7 is pinned. Both live in
  `hnfeed.android.roborazzi`.

## Image generation (`:core:imagegen`)

- **Two tiers: a preview, then an upgrade**: `ThumbnailGenerator.generatePreview` always succeeds with on-device
  art in well under a second, offline and without quota, so no card waits on the network. `generateUpgrade` tries
  the better generators (cloud today; another generator slots into the list) in order and reports what happened:
  `Upgraded`, `NotApplicable` (no credentials, or the safety filter keeps the title on device), `Rejected`
  (moderation or bad input: 4xx other than 401/403/404/408/429; a retry of this prompt would fail the same way),
  `Misconfigured` (HTTP 401/403/404: a bad or revoked token or a wrong account id, the same for every story),
  `QuotaExhausted` (HTTP 429 with Cloudflare's code 3036, the daily free allocation), `Unavailable` (no
  connection, DNS, a connect timeout, a failed TLS handshake, a connection dropped before any answer, 429 "capacity
  temporarily exceeded" or 502-504: nothing to do with the story) or `RetryLater` (a timeout waiting for the
  image, a reset of the request's HTTP/2 stream other than REFUSED_STREAM/CANCEL, HTTP 500, a response cut off
  midway, unreadable image). The pipeline decides what each outcome means. Every generator returns the same
  512x288 WebP.
- **Cloud: FLUX.1 [schnell] on Cloudflare Workers AI**. The plan was Pollinations' keyless API, but since October
  2026 it answers `402 Payment Required` for every new prompt, so a free keyless cloud option no longer exists.
  Cloudflare's free tier (10k neurons/day) covers about 170 images (about 58 neurons per 1024x1024 image at 4
  steps); after that the pipeline pauses upgrades until the quota resets. Generation takes about 2 s.
- **Prompt, tuned against the real model**: "illustration of the headline" made FLUX draw screens and documents
  full of pseudo-text, and negations ("no text") do not help. "Abstract geometric illustration, a visual metaphor
  for: <title>. Shapes and colour only, flat vector style, soft palette, layered composition filling a wide 16:9
  frame." gives wordless, on-topic images. The phrase "safe for work" is avoided: Cloudflare's prompt
  classifier flags it as NSFW.
- **Safety**: a small blocklist of sexual, violent and self-harm words, stems and phrases, matched on normalised
  whole words (so "Sussex" and "U.S. ex-president" pass). Flagged titles go on-device, where procedural art cannot
  depict anything. Cloudflare also screens prompts itself (it has no image "safe mode"); rejected prompts keep
  their preview. The list is one layer, not a classifier: it misses things and does not cover hate speech.
- **On-device: MediaPipe text embedder + procedural art**. Google's Universal Sentence Encoder (6 MB TFLite in
  assets, Apache-2.0, see `core/imagegen/NOTICE.md`) turns the title into 100 numbers; similar titles give nearby
  vectors. A painter maps the vector to palette, gradient, glowing orbs and a wave. The mapping is continuous
  (each parameter is a projection of the whole vector; hue is the angle of a 2-D projection), so related stories
  look related, and deterministic (a fixed SplitMix64 generator, not `kotlin.random`, whose sequence may change
  between Kotlin versions). If the model cannot load, art is seeded from the title text instead, never a crash.
- **Threading**: only HTTP runs on the IO dispatcher; JSON, base64, decoding (subsampled: 1024 px decodes at
  512 px), drawing and WebP encoding run on the CPU dispatcher; the embedder serialises MediaPipe calls.
- **Costs to know about**:
  - The API token is compiled into the APK and can be extracted. Acceptable for a personal build on a free, scoped token;
    production would call its own backend that holds the key, rate-limits and moderates.
  - MediaPipe's native library is large: 14 MB for arm64, 18 MB for x86_64. The `tasks-text` artifact also
    bundles a text-generation library the embedder never loads; the app excludes it and keeps only the arm64-v8a
    and x86_64 ABIs (phones and emulators). The release APK grows from 1.5 MB to 40 MB (21 MB compressed):
    native libraries are stored uncompressed and 16 KB aligned so they load straight from the APK. An App Bundle
    on Play would deliver one ABI: about 25 MB installed on an arm64 phone.
  - Inference runs fully on device, but MediaPipe may send anonymous usage statistics to Google.
- **Tests**: the chain, filter, prompt, encoder and Cloudflare client (MockWebServer with the real response
  envelope, including a moderation rejection and cancellation) run on the JVM; the painter is covered by
  determinism/similarity tests and a Roborazzi contact sheet (`core/imagegen/src/test/screenshots`). The real
  MediaPipe model and one real Cloudflare call run as instrumented tests; the Cloudflare one costs quota, so it
  only runs with `-Pandroid.testInstrumentationRunnerArguments.liveCloudflare=true` and credentials.

## Thumbnail pipeline (`:core:data`)

- **Phase 1, previews**: whenever the mediator writes a page (and when the feed is collected), a unique
  `ThumbnailPreviewWorker` gives every story in the visible feed without a thumbnail its on-device art, best rank
  first, a few in parallel. The feed shows it as soon as the row is written, a moment after the card appears. A
  story whose preview fails is tried again on the next page load or app start, not with backoff, which would hold
  every later preview run back.
- **Phase 2, upgrades**: the preview worker ends by enqueuing a unique `ThumbnailUpgradeWorker` (network required),
  so an upgrade run never starts before the previews it would upgrade exist. It replaces previews with cloud
  images, best rank first, two at a time, so the roughly 170 free images a day go to what the user actually sees.
  - `QuotaExhausted` stops the run and pauses upgrades until just after midnight UTC, when Cloudflare's daily
    allowance resets. The pause is persisted, so runs started meanwhile end without calling the API, and a separate
    delayed job (KEEP, outside the upgrade queue, so requests during the pause never pile up behind it) restarts
    upgrades after the reset. Until then cards keep their previews; nothing is lost.
  - `Unavailable` stops the run without counting against any story (otherwise a provider outage would make the
    best-ranked stories give up for good). `RetryLater` stops it too, but counts an attempt for that story, at most
    one per 15 minutes; after 3 it is given up, so one story that always times out slows the queue for about half an
    hour (see the trade-off below). The spacing matters because every page load starts a run, and each run
    tries the best-ranked pending stories first: without it, a few page loads during a short outage would use up
    their attempts within seconds.
  - Either way the run succeeds and a separate delayed job starts upgrades again after as long as the failures have
    been going on, at least 15 minutes and at most 4 hours: failures 0, 15, 30, 60 and 120 minutes into an outage
    retry after 15, 15, 30, 60 and 120 minutes, so the gaps roughly double. When the streak began is persisted next
    to the quota pause. The streak ends when a run finishes for any other reason (worked off, quota, refused
    credentials), or when the provider answered for some story (an image or a refusal) before another story's
    failure stopped the run. Short at first because most outages are brief; growing, so a provider that stays
    unreachable from this network costs about 6 background runs a day instead of one every 15 minutes. Based on time,
    not on the number of failed runs, because page loads and app starts also start runs: five failed runs while the
    user scrolls during a short outage still mean a 15-minute retry. The job is enqueued with REPLACE, so the latest
    failure decides its delay and a long retry left over from an earlier outage cannot hold back a new one. Not
    WorkManager's retry: a run in exponential backoff stays at the head of the upgrade queue, and WorkManager counts
    its backoff per request, not per story, so after a long outage, a captive portal or a provider that keeps timing
    out, every new request would wait behind it for up to 5 hours, even once the provider is back.
  - Trade-off, a story that always times out: its 3 counted attempts must be 15 minutes apart, and every run in
    between asks for it first and waits for its timeout (up to 30 seconds) before stopping. Runs request two stories
    at a time, so only the one requested alongside it is upgraded per run until it is given up, about half an hour
    after its first counted failure. Accepted because the spacing is what keeps the top
    stories from being given up during short provider hiccups; skipping stories that are cooling down would spread
    attempts over more stories during provider-wide outages and add complexity.
  - Trade-off, the streak counts idle time: it also grows while nothing is tried (e.g. offline for hours after a
    failure), so the first failure after a long offline period can schedule the background retry up to 4 hours out
    instead of 15 minutes. Accepted because page loads and app starts retry at once in the foreground anyway, and it
    avoids a second persisted timestamp.
  - The line between the two is drawn per failure: nothing that happens before Cloudflare has the request (DNS,
    connect, TLS) or that ends the whole connection (a dropped or closed connection, which on HTTP/2 can surface as
    a REFUSED_STREAM or CANCEL stream reset) counts against a story. A timeout waiting for the image, or a reset of
    just that request's stream with any other code, does: they may be about the prompt, and an uncounted one would
    retry the same story forever. The cost: if Cloudflare's servers reset one request's stream for reasons that
    have nothing to do with the prompt (stream resets are sent by the server inside TLS; a bad network drops whole
    connections instead), the story can use up its 3 attempts and keep its preview. Detecting stream resets uses
    OkHttp's `StreamResetException`, a public class in its internal package.
  - `Misconfigured` stops the run without touching any story and without backoff or a timed pause: the stories
    stay pending, and the next run (page load, app start) tries again, so fixing the token upgrades them.
  - `NotApplicable` and `Rejected` are final: the preview stays.
- **State in Room, images on disk**: a separate `thumbnails` table (story id, file name, source, upgrade state,
  attempts) so story upserts never touch it; the feed query left-joins it. It is the first schema change:
  version 2 uses a Room auto-migration, verified on a device by `MigrationTest` (`MigrationTestHelper` with the
  exported schemas). Rows cascade-delete with their story.
- **Files**: WebP in `filesDir/thumbnails`, one name per version (`<id>-on_device.webp`, `<id>-cloud.webp`), so
  an upgrade never overwrites a file the UI may be reading and Coil's cache never shows a stale image. Writes go
  to a temp file, synced (`fsync`) and renamed into place, so a crash cannot leave a half-written image under the
  real name. This is not full power-loss safety: the directory is not synced, so a power cut right after a write
  may lose the rename and leave a row whose file is missing (that card keeps its placeholder). The file is written
  before its row and the replaced file deleted after it, so a crash leaves at worst an orphan file, never a row
  pointing at nothing.
- **Every thumbnail write reloads the feed**: the feed query joins `thumbnails`, so each preview and each upgrade
  invalidates the feed `PagingSource`, about two reloads per story while images arrive. Acceptable here: Room
  reloads only a small window around the scroll position (20 rows), Compose skips the cards whose story did not
  change (keyed by id, stable and equal models), and the swap from placeholder to preview to cloud image comes
  for free. Paging placeholders keep list indices absolute, so a reload never moves the scroll position (see
  *Paging*). If it became a cost, the alternatives are batching writes (one transaction per batch, so one reload)
  or taking thumbnails out of the join and merging a separate `Flow<Map<storyId, Thumbnail>>` into the cards.
- **Database errors**: only a foreign-key failure means "the story was deleted meanwhile" and is skipped. Any other
  (e.g. a full disk) counts as a failure for that story. A preview run then skips the story; an upgrade run stops
  like at any transient failure, and the attempt is counted every time (not spaced like the provider's failures:
  the image was already paid for), so at most 3 paid images per story are lost and the quota is not spent on it
  again and again.
- **Cleanup**: after the preview pass, stories that are no longer in the snapshot and were fetched over 7 days ago
  are deleted (their rows go with them) and files no row references are swept; files younger than 10 minutes are
  spared, since their row may not be committed yet.
- **Cards**: Coil loads the file over the placeholder with a crossfade. When a preview is upgraded the request
  uses the image on screen as its placeholder (by memory cache key), so the card goes straight from preview to
  cloud image without flashing the placeholder. Previews and screenshot tests draw thumbnails with Coil's preview
  handler, so they need no files.
- **WorkManager with Hilt**: workers are `@HiltWorker`s; the app supplies `HiltWorkerFactory` through
  `Configuration.Provider` and removes WorkManager's default initializer from the manifest.
- **Tests**: the pipeline runs against an in-memory Room database, a temp directory and a fake generator
  (ordering, idempotence, failures that must not loop, stories deleted mid-generation, quota pause and resume,
  unavailable vs. per-story failures, give-up after 3 attempts, cleanup). Workers are tested with
  `TestListenableWorkerBuilder`, the scheduler against WorkManager's test implementation (`TestDriver`: network
  constraint, quota-resume and outage-retry delays, unique-work policies, a request after an outage not waiting
  for a backoff); the store and the pause have their own tests.

## On-device diffusion (investigated, dropped)

Fully on-device text-to-image as a third thumbnail tier was tried and left out. Google's MediaPipe generator runs on a
Pixel 7 Pro but draws noise on its GPU; the model was verified byte for byte against the official weights, which
points to the engine on this GPU and driver. The investigation, the alternatives and what the one plausible path
(stable-diffusion.cpp on the CPU) would take are in [on-device-diffusion.md](on-device-diffusion.md).

## Story and comments (`:feature:story`)

- **Comments are an adjacency list in Room**: every row has a parent (the story for top-level comments) and a rank
  among its siblings, the order of the parent's `kids` in the API. One flat table holds the whole tree; the UI
  flattens what is expanded (`buildCommentList`, a pure function with its own tests).
- **Stubs before content**: loading a comment also writes its replies as id-only rows (`is_loaded = 0`). Every level
  therefore knows how many replies it has and which to fetch next before fetching any, also offline. Items the
  API cannot return (unknown ids, wrong type) are removed. The API lists deleted replies too: once loaded, those
  without replies of their own are hidden and no longer counted, but until then "Show 12 replies" may include
  some.
- **Lazy, by levels**: opening a story refreshes it and fetches its first 20 top-level comments (one request each,
  in parallel: the API has no batch endpoint). More top-level comments load as the user scrolls to the end. Replies
  load only when the user expands a comment, 20 at a time. A story with 300 comments costs about 21 requests to
  open, not 300.
- **Refresh reconciles, never wipes**: a refresh re-ranks the top level, removes comments that disappeared and
  re-fetches the first page (comments can be edited for two hours), but keeps everything else loaded. Network
  first, then one transaction, so a failed refresh or "load more" leaves the cache as it was and surfaces as an
  error on that row (retry), or as a banner above cached content. A "load more" checks inside its transaction
  that its comments are still the next ones to load (like the feed's APPEND), so a refresh that re-ranked or
  removed them meanwhile wins and the stale page is dropped.
- **Trade-off: re-ranking under the reader**: HN reorders comments by score. After a refresh, comments beyond the
  first page that were already loaded keep showing, and newly ranked ones load in above them when the user
  reaches the "more" row. The final order is right; showing only up to the first unloaded comment would hide
  content the user had already read.
- **Deleted and flagged comments** stay as "[deleted]" / "[flagged]" placeholders only while they have replies, as
  on Hacker News; flagged text is not stored. Comments by the story's author get an "OP" badge.
- **HTML**: comment and Ask HN bodies are the API's HTML, rendered with Compose's `AnnotatedString.fromHtml`
  (paragraphs, italics, code, tappable links; only `http(s)` links open). Nothing in it is executed. The parser
  ignores `<pre>` and collapses whitespace, so code blocks are rewritten with `<br>` and non-breaking spaces first.
- **Navigation guards**: a quick double tap could otherwise open a story twice or pop the feed as well (an empty
  back stack makes `NavDisplay` throw), so each callback acts only while its entry is on top.
- **Expanded threads are UI state**: which comments are expanded lives in the ViewModel's `SavedStateHandle`, so it
  survives process death; the comments themselves are in Room.
- **Navigation 3** in `:app`: a saveable back stack of serializable keys (`FeedKey`, `StoryKey(id)`), with entry
  decorators that give each entry its own saved state (the feed keeps its scroll position) and its own
  `ViewModelStore` (each story gets its own `StoryViewModel`, created with the id through Hilt assisted injection).
  Features know nothing about navigation; the feed reports a story id. Implemented with Google's official
  `navigation-3` agent skill (`.claude/skills/navigation-3`, trimmed to the recipes this app uses).
- **Shared UI in `:core:ui`**: the story thumbnail, labels, byline and load-state components moved out of the feed
  so both screens use the same ones, and `PreviewTheme` makes previews, screenshot and UI tests deterministic.
- **Not yet**: poll options (`pollopt` items) are not shown; collapsing a whole subtree beyond hiding replies, and
  refreshing replies below the first level, are left out.

## Offline, accessibility, transitions and app-level tests

- **Offline awareness**: a `NetworkMonitor` (`ConnectivityManager` callbacks as a `Flow`, as in Now in Android)
  tells screens whether a network is up. Offline, they say so instead of reporting a failed refresh, and when the
  connection returns they retry what failed (Paging's `retry()` for the feed, also when the feed comes back on
  screen after reconnecting elsewhere; the refresh and failed comment pages for a story).
  - "Online" means a network Android has *validated*, so Wi-Fi behind a captive portal does not trigger retries
    that would fail. Trade-off: a network that never validates shows as offline, though requests are still tried
    and their failures handled as usual.
  - The banner sits above the list, not in it: an item inserted at the top of a keyed list lands above the first
    visible item, off-screen, where TalkBack would never announce it.
- **TalkBack**: a comment is one item (author, time, text) with its replies button after it, replies announce
  their nesting level, the "OP" badge reads "Story author", the offline note is a polite live region, and screen
  and section titles are headings. Links inside a merged comment still open. Covered by UI tests on the semantics
  tree.
- **Large fonts**: screenshot tests at 200% (Android's maximum) for the feed and nested comments. Feed titles
  stop at three lines (the full title is on the story screen and read in full by TalkBack).
- **Shared thumbnail transition**: a `SharedTransitionLayout` around `NavDisplay`; the app hands each entry's
  animation to features through two composition locals in `:core:ui`, so features need no navigation dependency
  and previews and tests (without those scopes) render normally. Screens change with Material's "fade through"
  (90 ms out, 210 ms in) so their text never overlaps while the picture moves. Both thumbnails use the file path
  as their memory cache key, so the story header shows the card's image on its first frame instead of a
  placeholder. An app-level test with real image files pauses the Compose clock halfway and keeps that frame as
  a screenshot.
- **App-level UI tests on Robolectric**: `MainActivity` with Hilt, real navigation, ViewModels and screens, with
  `DataModule` and `ClockModule` replaced by fakes (`@TestInstallIn`). They cover what screen tests cannot:
  moving between screens, system and top-bar back, state across recreation, offline on both screens. They need
  no emulator, so CI can run them. (`DataModule` is public only so tests can replace it; its bindings stay
  internal.)
