# Third-party skills

Copied unmodified from Google's official [android/skills](https://github.com/android/skills) repository
(Apache-2.0, see each skill's `LICENSE.txt`; upstream ships `navigation-3` without one, so it has a copy of the
repository's root `LICENSE.txt`). Do not edit them in place: to customise one, copy it under a new
name, so updating from upstream never overwrites local changes.

| Skill | Upstream path | Commit |
| --- | --- | --- |
| `agp-9-upgrade` | `build-system/agp/agp-9-upgrade` | `95ea931a1ad8f9b2be132ae0cfb2aad964d71118` |
| `edge-to-edge` | `system/edge-to-edge` | `95ea931a1ad8f9b2be132ae0cfb2aad964d71118` |
| `navigation-3` | `navigation/navigation-3` | `95ea931a1ad8f9b2be132ae0cfb2aad964d71118` |

To update: re-download the path at a newer commit (or `android skills add <name>` with the Android CLI) and
bump the commit in this table.

Planned additions, each with the PR that needs it: `testing-setup` (screenshot/UI tests), `r8-analyzer`
(release hardening).

Project-specific skills (`verify-build`, `open-pr`, and the feature skills added per PR) are written for this repo.
