# Module lib-oauth2-server-authorization-impl

Runtime for the Authorization Server contracts declared in `lib-oauth2-server-authorization-public`. It realises the full authorization-server command graph: authorization code, session and response construction, attestation-based challenges, and the discovery metadata endpoint. Supports authorization code, PAR, JAR, and attestation flows in one coherent graph.

This is the module a deployable OAuth2/OIDC AS service (for example `services-oauth2-as-rest`) plugs in under the hood.
