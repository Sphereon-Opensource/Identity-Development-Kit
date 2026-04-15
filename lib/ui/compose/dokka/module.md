# Module lib-ui-compose

Shared Compose Multiplatform primitives for component-level theming. It defines the component-token surface (the fine-grained tokens an individual Compose component reads from its environment) and the CompositionLocal that carries them through the tree, so that downstream component libraries do not each have to invent their own token plumbing.

Pair with `lib-conf-theme-compose`, which resolves IDK's tenant-scoped branding into the concrete token values this module exposes to the composition.
