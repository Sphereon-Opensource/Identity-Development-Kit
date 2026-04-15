# Module services-oid4vci-issuer-rest

Deployable Ktor server that exposes an OID4VCI issuer over HTTP. It mounts the OID4VCI endpoint surface (credential offer, issuer metadata, credential, deferred credential, and the issuer-metadata variants for JWT VC profiles) on top of the OID4VCI issuer runtime, producing a deployable issuer service.

The issuer itself needs an Authorization Server: either delegate to an external AS, or run `services-oauth2-as-rest` alongside. The issuer behaviour (metadata, offer construction, credential assembly) is configured via the module's configuration contracts; credential-claim projection comes from the application's credential-attribute contributor rather than being hardcoded here.
