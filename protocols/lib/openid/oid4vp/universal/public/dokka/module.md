# Module lib-openid-oid4vp-universal-public

Public API for the OID4VP universal service layer, which sits above the raw verifier runtime and coordinates authorization-request sessions across request, status, and delete phases. It is the surface a deployable verifier service works against when it has to manage many concurrent OID4VP sessions (create, poll for status, tear down) rather than orchestrate a single request.

Depend on this module from verifier-service code that needs the session-level contract. Runtime implementations live in `lib-openid-oid4vp-universal-impl`; the HTTP-exposed deployment lives in `services-oid4vp-verifier-rest`.
