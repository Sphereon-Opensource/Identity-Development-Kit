# Module lib-oauth2-client-public

Public OAuth2 client contract. It defines the client surface and the command bindings for the authorization-code lifecycle, JAR (JWT-secured authorization requests), PAR (pushed authorization requests), metadata discovery, and token exchange. The API is deliberately protocol-focused: higher-level flows (for example OID4VCI holder) build on top of these commands rather than replicating them.

Depend on this module from code that has to drive an OAuth2 client at the protocol level. Runtime implementations live in `lib-oauth2-client-impl`.
