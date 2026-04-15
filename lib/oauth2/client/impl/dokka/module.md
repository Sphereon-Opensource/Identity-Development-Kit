# Module lib-oauth2-client-impl

Runtime for the OAuth2 client contracts declared in `lib-oauth2-client-public`. It handles the client-side flows end to end: constructing authorization-request URLs (including JAR and PAR variants), applying client authentication to token requests, and parsing authorization and token responses.

Depend on this module from any code that has to act as an OAuth2 or OID4VCI holder at the protocol level.
