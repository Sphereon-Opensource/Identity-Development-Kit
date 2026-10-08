# Module lib-oauth2-common-impl

Runtime for the shared OAuth2 / OIDC command surface declared in `lib-oauth2-common-public`. It supplies the pieces both clients and servers need in identical form: id_token validation, JARM (JWT-secured authorization response mode) creation and verification, and OIDC token-claim extraction, so these concerns do not get re-implemented per side.