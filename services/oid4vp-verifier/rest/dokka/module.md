# Module services-oid4vp-verifier-rest

Deployable Ktor server that exposes an OID4VP verifier over HTTP. It mounts the universal OID4VP service layer (`lib-openid-oid4vp-universal-impl`) behind REST endpoints for creating authorization requests, polling their status, and tearing them down, which is the shape an embedding application typically wants from a verifier: stateful session management, not per-request orchestration.

The actual presentation protocol, DCQL handling, and trust decisions come from the OID4VP runtime and the trust framework modules pulled in transitively. This service is the HTTP-facing boundary; the orchestration logic lives in the underlying libraries.
