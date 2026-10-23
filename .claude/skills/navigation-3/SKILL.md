---
name: navigation-3
description: Jetpack Navigation 3 reference for this app (trimmed from Google's official skill) - NavKey and
  NavDisplay basics, a saveable back stack, passing navigation arguments to Hilt ViewModels, modular navigation
  with Hilt, per-entry lifecycle, and destination animations.
license: Complete terms in LICENSE.txt
metadata:
  author: Google LLC
  last-updated: '2026-10-17'
  trimmed: Only the parts this app uses or plausibly will; see .claude/skills/THIRD_PARTY.md for what was removed.
  keywords:
  - Navigation 3
  - Compose
  - NavKey
  - NavDisplay
  - Hilt
  - ViewModel
  - animations
  - lifecycle
---

*** ** * ** ***

## Developer documentation

- *[Navigation 3](references/android/guide/navigation/navigation-3/index.md)*. Search documentation for more information on basics, saving and managing navigation state, modularizing navigation code, creating custom layouts using Scenes, animating between destinations, or applying logic or wrappers to destinations.

## Recipes

Code examples showcasing common patterns.

### Basic API usage

- *[Saveable back stack](references/android/guide/navigation/navigation-3/recipes/basicsaveable.md)*: Shows basic API usage with a persistent back stack.

### Animations

- *[Animations](references/android/guide/navigation/navigation-3/recipes/animations.md)*: Shows how to override the default animations for all destinations and a single destination.

### Lifecycle

- *[Lifecycle Owner](references/android/guide/navigation/navigation-3/recipes/lifecycle-owner.md)*: Shows how to use and observe Lifecycle in Navigation 3.

### Architecture

- *[Modularized navigation code (Hilt)](references/android/guide/navigation/navigation-3/recipes/modular-hilt.md)*: Demonstrates how to decouple navigation code into separate modules using Hilt or Dagger for DI.

### Working with ViewModel

#### Passing navigation arguments

- *[ViewModel arguments (Hilt and basic)](references/android/guide/navigation/navigation-3/recipes/passingarguments.md)* : Navigation arguments are passed to a `ViewModel`, with Hilt assisted injection or with `viewModel()` and a custom factory.
