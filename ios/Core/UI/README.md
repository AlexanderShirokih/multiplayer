# iOS Core UI

`CoreUI` is the SwiftUI design-system foundation module for MultiPlayer on iOS.

## What belongs here

- `MultiplayerDesignSystem` as the single entry point for app theming
- Foundation design tokens that define app-specific visual rules
- Thin SwiftUI wrappers that keep feature code decoupled from raw styling decisions
- Preview helpers for fast UI iteration

## Token rule

Add a MultiPlayer token when the value expresses product language that should stay stable across features:

- semantic colors like `textPrimary` or `surfaceOverlay`
- spacing steps like `md` or `xl`
- corner radii like `medium` or `pill`
- semantic elevation styles reused across screens

Do not mirror every native SwiftUI API into custom tokens. Prefer SwiftUI defaults when the platform already provides a good semantic contract.

Typography should stay minimal and semantic:

- keep only roles that repeat across screens, such as page titles, content titles, body, meta, and labels
- merge near-duplicates instead of creating a new style for a 1-2pt deviation
- keep one-off hero or component-specific text metrics local to the component instead of promoting them into `CoreUI`

Colors should stay minimal and semantic:

- prefer a single semantic role over multiple shade aliases that differ only for one component
- keep paired gradient colors grouped under one semantic ramp instead of exporting separate `start/end` tokens
- if a tint is only needed inside one illustration or decorative pattern, derive it locally with opacity instead of promoting a new global token

## SwiftUI rule

- Feature modules should prefer `MultiplayerDesignSystem`, `MultiplayerSurface`, `MultiplayerText`, and `@Environment(\\.multiplayerTheme)`
- Theme values flow through SwiftUI `Environment`, which is more idiomatic on iOS than global theme singletons
- Wrappers should stay thin and predictable; they may provide app defaults, but should not hide SwiftUI behavior
