# Third-party skills

From Google's official [android/skills](https://github.com/android/skills) repository (Apache-2.0, see each
skill's `LICENSE.txt`; upstream ships `navigation-3` without one, so it has a copy of the repository's root
`LICENSE.txt`). Do not edit them in place except as recorded below, so updating from upstream stays a matter of
re-downloading and re-applying these notes.

| Skill | Upstream path | Commit | Changes |
| --- | --- | --- | --- |
| `agp-9-upgrade` | `build-system/agp/agp-9-upgrade` | `95ea931a1ad8f9b2be132ae0cfb2aad964d71118` | none |
| `edge-to-edge` | `system/edge-to-edge` | `95ea931a1ad8f9b2be132ae0cfb2aad964d71118` | none |
| `navigation-3` | `navigation/navigation-3` | `95ea931a1ad8f9b2be132ae0cfb2aad964d71118` | trimmed, see below |

`navigation-3` is trimmed to what this app uses or plausibly will: the guide index and the recipes
`basicsaveable`, `passingarguments` (Hilt ViewModel arguments), `modular-hilt`, `lifecycle-owner` and
`animations`. Removed: the Navigation 2 migration guide and type-safe destinations guide, and the recipes for
`basic`, `basicdsl`, bottom sheets, common UI, conditional navigation and transitions, deep links (4), dialogs,
Material list-detail and supporting pane, Koin, multiple back stacks, returning results (2), and list-detail and
two-pane scenes. `SKILL.md` lists only the kept references, and its description says so. The removed files are in
the upstream commit above if a later feature needs them.

To update: re-download the path at a newer commit (or `android skills add <name>` with the Android CLI), re-apply
the changes above, and bump the commit in this table.

Planned additions, each with the PR that needs it: `testing-setup` (screenshot/UI tests), `r8-analyzer`
(release hardening).

Project-specific skills (`verify-build`, `open-pr`, and the feature skills added per PR) are written for this repo.
