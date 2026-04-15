# Module services-oauth2-as-rest

Deployable Ktor server that exposes an OAuth2 / OIDC Authorization Server over HTTP. It mounts the authorization-server command graph from `lib-oauth2-server-authorization-impl` behind standard OAuth2 and OIDC endpoints (authorization, token, introspection, and so on), producing a ready-to-deploy AS that the rest of the platform (including the OID4VCI issuer) can federate against.

Use this when an IDK-based deployment needs its own AS rather than delegating to an external IdP. For OID4VCI specifically, pair with `services-oid4vci-issuer-rest`; the two services are designed to be operated together.
