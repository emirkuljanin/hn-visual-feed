# Roadmap

Where the app goes next, in order. Each bullet is one reviewed PR unless it says otherwise; the milestones are
meant to ship one after the other, with a release at the end of each. Trade-offs of what already exists are in
[decisions.md](decisions.md).

## 1. Foundations

- **Remove cloud image generation**: delete the Cloudflare client and everything only it uses (the upgrade tier in
  `TieredThumbnailGenerator`, `UpgradePause`, the upgrade and resume workers, the upgrade columns, through a Room
  migration). Nobody installing a public APK could ever see these images, since the token cannot ship in one. Keep a
  short write-up in `docs/`: cloud images are doable, but costly, because they need a backend to hold the token, rate
  limits against abuse, and paid quota beyond about 170 free images a day. New README screenshots come with
  milestone 2, since the current feed shot shows cloud images.
- **Rename to Folio**: a real product name and launcher icon, and the repository to match. The package name stays
  `hnfeed`, since renaming it brings nothing to a user. Check that the name is free before committing to it.
- **Apache-2.0 licence**: the repository has none today.
- **Signed releases**: a real keystore, so updates install over each other. The key lives in a CI secret and signing
  runs in a job without Gradle, so third-party build plugins never see it. Installing the first signed build needs
  one uninstall.
- **AGP 9.5**: Dependabot's update fails lint because an androidx lifecycle lint check uses an API missing from AGP
  9.5's lint ([#5](https://github.com/emirkuljanin/hn-visual-feed/pull/5)). Lifecycle 2.11.0 is already the latest
  stable, so this waits for a fixed release or needs a documented lint exception.
- **Release v1.1.0**: `v1.0.0` predates the fixes in #3 and #4.

## 2. Look

- **Topic-aware art**, the default thumbnail: use the bundled embedder to classify titles by topic (security, AI,
  hardware and so on), each with its own visual style, so cards can be told apart at a glance.
- **Editorial redesign**: a hero card for the top story, a custom typeface, Material 3 Expressive. Screenshot
  baselines are re-recorded.
- **Motion**: shimmer placeholders, thumbnail parallax, a collapsing header, press and enter animations.
- **Demo video and new screenshots** at the top of the README.

## 3. Measure

- **Baseline profile and Macrobenchmark**: real startup and scroll numbers in the README, and a check in CI.
- **Firebase Crashlytics and Performance**, on for every user with no opt-out, and stated in the README. Debug builds
  get an `applicationIdSuffix` and their own Firebase app, so debug and release reports never mix.
- **Architecture write-up**: stable paging, offline first and the thumbnail pipeline, with diagrams.

## 4. Reader features

- **Search and other lists**: New, Best, Ask and Show, plus search through Algolia's HN API. The snapshot table and
  mediator generalise from one feed to several, so this goes first.
- **Saved stories, read state and background sync**, so offline covers more than what has already been loaded.
- **Settings screen**, starting with a toggle for showing the article's image, with the art as its fallback.
- **Tablet and reader**: list-detail on wide screens with Navigation 3 scenes, and Custom Tabs or a reader view
  instead of the external browser.
- **Smaller gaps**: poll options, collapsing whole comment subtrees, a maximum text width for comments on wide
  screens (about 110-120 characters per line today).

## Parked

- **AI summaries** of articles and comment threads: waiting for a test phone that runs Gemini Nano. The current one,
  a Pixel 7 Pro, is not on the supported lists.
- **On-device diffusion**: see [on-device-diffusion.md](on-device-diffusion.md). It would need new work on the
  thumbnail pipeline once the upgrade tier is gone.
