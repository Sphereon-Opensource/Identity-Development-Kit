# Module lib-conf-theme-core-public

Public theming and branding model. It defines the tenant-scoped branding metadata, CSS and media policy configuration, and default design-token primitives that let IDK apply a consistent visual identity across mobile (Compose) and web surfaces without the core layer knowing anything about a specific rendering stack.

Depend on this module when you need to read or describe branding configuration in an otherwise rendering-agnostic module. Runtime resolution lives in `lib-conf-theme-core-impl`; surface-specific bindings live in `lib-conf-theme-compose` and `lib-conf-theme-web`.
