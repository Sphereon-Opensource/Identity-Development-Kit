# Module ktor-server-kotlin-inject

Ktor server plugin that bridges IDK's DI-scoped services into request handling. It lets route handlers resolve services from the same dependency graph that powers the rest of an IDK application, and derives per-request child graphs so that request-scoped state (tenant, principal, session) can be looked up cleanly from within a handler.

The artifact name is historical: the plugin was originally written against kotlin-inject. The current implementation targets Metro (`dev.zacsweers.metro`), but the Gradle coordinates are kept stable so that downstream consumers are not forced to rename. New modules should not take the artifact name as an indication of the underlying framework.

Reach for this module when you are building a Ktor service on top of IDK and want route handlers to fail fast on missing request context instead of re-resolving services by hand.
