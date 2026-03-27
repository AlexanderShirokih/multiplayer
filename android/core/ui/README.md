# Android Core UI

`core/ui` is the Android design-system foundation module for MultiPlayer.

## What belongs here

- `MultiplayerDesignSystem` as the single entry point for app theming
- Foundation design tokens that define app-specific visual rules
- Thin wrappers over Material 3 that keep feature code decoupled from raw `MaterialTheme`
- Preview helpers for fast UI iteration

## What counts as a design token

A token is a stable, semantic design decision that should be shared across screens and features:

- colors like `textPrimary` or `surfaceSecondary`
- spacing steps like `md` or `xl`
- corner radii like `medium` or `pill`
- elevation levels used repeatedly across UI

Do not add a token when Material 3 already provides a good public contract and the app does not need its own semantic alias.

## Token vs Material rule

- Add a new MultiPlayer token when the value represents product language that should stay stable even if Material internals change
- Use `MaterialTheme` through `MultiplayerTheme.materialColorScheme` or `MultiplayerTheme.typography` when the code needs standard Material semantics
- Avoid mirroring every Material token one-to-one into MultiPlayer tokens

## Wrapper rule

- Feature modules should prefer `MultiplayerDesignSystem`, `MultiplayerTheme`, `MultiplayerSurface`, and `MultiplayerText`
- Direct `MaterialTheme` usage should stay inside `core/ui`, except for rare interop cases
- Wrappers should stay thin and predictable; they may set app defaults, but should not hide important Material behavior
