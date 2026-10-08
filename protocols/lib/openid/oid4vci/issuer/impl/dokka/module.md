# Module lib-openid-oid4vci-issuer-impl

Runtime for the OID4VCI issuer contracts declared in `lib-openid-oid4vci-issuer-public`. It realises core issuer behaviour: building issuer metadata (signed and unsigned), creating credential offers, bridging to the authorization server, and providing a default (no-op) attribute contributor that downstream code typically replaces with its own projection from business data onto credential claims.

The higher-level REST service layer lives in `lib-openid-oid4vci-rest-*`; the deployable server lives in `services-oid4vci-issuer-rest`.
