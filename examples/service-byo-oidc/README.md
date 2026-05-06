# BYO OIDC example service

A minimal Ktor service demonstrating the **Bring-Your-Own OIDC** deployment
path on pure IDK. The service:

1. Builds an IDK `AppGraph` wired to one `IdpConfig`.
2. Installs two Ktor plugins in order:
   - `KotlinInjectPlugin` attaches the Metro AppGraph to the call pipeline and
     builds a per-request session graph.
   - `JwtAuthentication` validates the bearer token, resolves identity, and
     stashes a `SessionContext`. With `KotlinInjectPlugin` installed, its
     default resolvers pull the following IDK services straight out of the
     call-attached graphs; no bridge code is needed.
     - `DefaultJwtValidationService` (session-scoped)
     - `VerifyJwtCommandImpl` -> `JwtServiceImpl` -> `VerifyJwsCommandImpl`
     - `JwksUrlExternalIdentifierResolutionServiceImpl` for JWKS retrieval
     - The default `IdentityResolutionPipeline` (`OidcPrincipalResolver`)
     - The default `SessionContextFactory`
3. Exposes `GET /api/v1/me` which reads the `SessionContext` that the plugin
   stashes on the call and returns `{ sessionId, tenantId, principal }`.

What is "bring your own" is the **IdP** (Keycloak, Okta, Entra ID, ...), not
the validator. The validator is IDK's stock session-scoped
`JwtValidationService`, resolved per-request by `JwtAuthentication`'s default
lambda (`call.getSessionService<JwtValidationService>()`) out of the session
graph that `KotlinInjectPlugin` attached to the call. Extension graphs
published by `ktor-server-jwt-auth` (`JwtAuthSessionExtensionGraph` and
`JwtAuthAppExtensionGraph`) expose the required accessors so Metro's
reflection-based service lookup finds them without any per-deployment
wiring.

## Configuration

| Env var            | Required | Description                                           |
|--------------------|----------|-------------------------------------------------------|
| `AUTH_IDP_ISSUER`  | yes      | Issuer URL, e.g. `https://keycloak.example.com/realms/demo` |
| `AUTH_AUDIENCE`    | no       | Expected `aud` value. Defaults to `byo-demo`.         |
| `PORT`             | no       | Listen port. Defaults to `8080`.                      |

## Run

```bash
./gradlew :Identity-Development-Kit:examples-service-byo-oidc:run \
  -DAUTH_IDP_ISSUER=https://keycloak.example.com/realms/demo \
  -DAUTH_AUDIENCE=byo-demo
```

Then:

```bash
curl -H "Authorization: Bearer <access-token>" http://localhost:8080/api/v1/me
```

## Purity check

```bash
./gradlew :Identity-Development-Kit:examples-service-byo-oidc:checkIdkPurity
```

## E2E test

The `ByoOidcE2ETest` boots a Keycloak testcontainer, imports the `demo`
realm, mints a user token via ROPC, and asserts the full round trip through
IDK's validator (JWKS fetched from the Keycloak container, signature
verified end-to-end, claims resolved). The test skips gracefully when
Docker is not available.
