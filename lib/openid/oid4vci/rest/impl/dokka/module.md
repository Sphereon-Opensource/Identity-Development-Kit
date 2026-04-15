# Module lib-openid-oid4vci-rest-impl

Service-layer runtime that sits between the raw OID4VCI issuer commands and the deployable REST server. It realises the session-oriented endpoints a typical deployment needs (create, delete, and status for credential offers), backed by a key-value credential-offer session store.

Used by `services-oid4vci-issuer-rest` as the in-process service surface.
