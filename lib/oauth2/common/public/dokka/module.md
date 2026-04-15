# Module lib-oauth2-common-public

Shared OAuth2 / OIDC command contracts used identically by clients and servers: client authentication, DPoP proof handling, id_token operations, token introspection, and revocation. Centralising these avoids subtle drift between client and server implementations of the same primitives.

Depend on this module from either side of an OAuth2 exchange. Runtime implementations live in `lib-oauth2-common-impl`.
