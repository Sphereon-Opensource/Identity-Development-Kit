# Module lib-oauth2-server-authorization-public

Public command API for building an OAuth2 / OIDC Authorization Server. It defines the endpoint-oriented command surfaces (authorization, client authentication, discovery, attestation, IAE, id_token) that the deployable AS services call into to drive each protocol endpoint.

Depend on this module from code that has to describe AS behaviour. Runtime implementations live in `lib-oauth2-server-authorization-impl`.
