# Module lib-conf-theme-core-impl

Runtime that realises the theming contracts declared in `lib-conf-theme-core-public`: it resolves branding metadata for a tenant and caches the result so downstream Compose or web layers do not re-derive tokens on every render.

Pair it with one of the surface-specific modules (`lib-conf-theme-compose`, `lib-conf-theme-web`) to apply the resolved theme to an actual UI.
