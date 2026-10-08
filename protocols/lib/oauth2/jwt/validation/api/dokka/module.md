# Module lib-oauth2-jwt-validation-api

Public JWT validation contracts for OIDC-fronted services: the validation-service surface, the JWKS provider abstraction, and the IdP registry and configuration types. It is what resource servers or OID4VCI issuers depend on when they need to validate a JWT without baking in a specific IdP or JWKS fetch strategy.

Runtime implementations live in `lib-oauth2-jwt-validation-impl`.
