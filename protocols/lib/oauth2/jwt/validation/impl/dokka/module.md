# Module lib-oauth2-jwt-validation-impl

Runtime JWT validation for OIDC-fronted services. It discovers IdP metadata, caches JWKS, and verifies signatures, issuer, and audience claims against the registered IdP configuration, realising the contracts in `lib-oauth2-jwt-validation-api`.