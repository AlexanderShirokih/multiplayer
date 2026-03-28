# Android Core UI

`core/ui` is the Android design-system foundation module for MultiPlayer.

## What belongs here

- `MultiplayerDesignSystem` as the single entry point for app theming
- Foundation design tokens that define app-specific visual rules
- Thin wrappers over Material 3 that keep feature code decoupled from raw `MaterialTheme`
- Preview helpers for fast UI iteration

## What counts as a design token

A token is a stable, semantic design decision that should be shared across screens and features:

- colors like `textPrimary`, `surfaceOverlay`, or `surfaceContentPrimary`
- spacing steps like `md` or `xl`
- corner radii like `medium` or `pill`
- elevation levels used repeatedly across UI

Do not add a token when Material 3 already provides a good public contract and the app does not need its own semantic alias.

Typography should stay minimal and semantic:

- keep only roles that repeat across screens, such as page titles, content titles, body, meta, and labels
- merge near-duplicates instead of creating a new style for a 1-2sp deviation
- keep one-off hero or component-specific text metrics local to the component instead of promoting them into `core/ui`

Colors should stay minimal and semantic:

- prefer a single semantic role over multiple shade aliases that differ only for one component
- keep paired gradient colors grouped under one semantic ramp instead of exporting separate `start/end` tokens
- if a tint is only needed inside one illustration or decorative pattern, derive it locally with opacity instead of promoting a new global token

## Token vs Material rule

- Add a new MultiPlayer token when the value represents product language that should stay stable even if Material internals change
- Use `MaterialTheme` through `MultiplayerTheme.materialColorScheme` or `MultiplayerTheme.typography` when the code needs standard Material semantics
- Avoid mirroring every Material token one-to-one into MultiPlayer tokens

## Wrapper rule

- Feature modules should prefer `MultiplayerDesignSystem`, `MultiplayerTheme`, `MultiplayerSurface`, and `MultiplayerText`
- Direct `MaterialTheme` usage should stay inside `core/ui`, except for rare interop cases
- Wrappers should stay thin and predictable; they may set app defaults, but should not hide important Material behavior
