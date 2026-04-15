# Module lib-oauth2-server-resource-public

Public command API for an OAuth2 / OIDC Resource Server: the access-token validation, introspection, and DPoP-proof verification surface, plus the token and DPoP-nonce cache contracts.

Depend on this module from any service that has to accept OAuth2-protected traffic and wants to stay independent of a particular token store. Runtime implementations live in `lib-oauth2-server-resource-impl`.
