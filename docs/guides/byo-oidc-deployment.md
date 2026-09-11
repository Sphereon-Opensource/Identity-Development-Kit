# IDK BYO OIDC deployment guide

This guide describes how to deploy a pure-IDK service that authenticates requests against an external OIDC Identity Provider (Keycloak, Okta, Microsoft Entra ID, Auth0, or any OIDC-compliant provider).

The IDK open-source core ships the full JWT validation stack needed to run this deployment out of the box. No EDK (commercial) or VDX (product) modules are required, and a Gradle `checkIdkPurity` task enforces that boundary in CI.

See the runnable reference at `examples/service-byo-oidc/`: a three-test end-to-end harness that boots a Keycloak testcontainer, mints a token, and asserts the IDK stack validates it.

---

## What "BYO" means

"Bring Your Own" refers to the upstream **Identity Provider**, not the validator.

You bring:

- An OIDC-compliant IdP that hosts users, issues JWT access tokens, and exposes a JWKS endpoint. Keycloak, Okta, Entra ID, Auth0, Google Identity, Ping, and others all qualify.
- The issuer URL, expected audience, and (optionally) tenant claim configuration.

IDK provides:

- JWT parsing and structured error classification (`DefaultJwtValidationService`, post A-5/A-6).
- Signature verification against the upstream JWKS (`VerifyJwtCommand`, `JwtService`, `VerifyJwsCommand`).
- Identity resolution from validated token claims (`IdentityResolutionPipeline`, `OidcPrincipalResolver`).
- Session context construction from the resolved identity (`SessionContextFactory`).
- Ktor binding of the above into an `install(JwtAuthentication) { ... }` plugin.

---

## Scope limits

IDK BYO is **single-IdP-per-deployment**. Exactly one upstream OIDC issuer is registered at startup via `JwtValidationConfig.defaultIdp`, and every incoming token is validated against that issuer.

If your requirements include any of the following, IDK alone is not sufficient and you need the commercial EDK layer:

- Multiple upstream OIDC providers with per-tenant routing (tenant A uses Keycloak, tenant B uses Entra ID).
- Dynamic IdP registration at runtime (admin REST API that adds or removes IdPs without redeploying).
- Per-tenant assurance policy (tenant-specific mappings from upstream `acr` / `amr` to LoA).
- Federation session persistence across multiple service replicas (`FederationSessionStore` backed by Postgres).
- Hybrid mode where some tenants use an own AS and others BYO an external IdP.

The action plan at `docs/reviews/2026-04-22-auth-layer/ACTION-PLAN.md` documents the EDK work that delivers these (WP-B, "EDK commercial layer"). The rule of thumb: if the feature is multi-IdP or per-tenant-IdP, it lives in EDK, not IDK.

Multi-tenant tenant **resolution** (extracting a tenant id from a claim that the single IdP emits) is in scope for IDK and works in the BYO path. What IDK does not do is route tokens to different IdPs based on tenant.

---

## Minimum IDK module set

A BYO OIDC service depends on the following IDK modules at runtime. All are published to the IDK Maven artifacts under the `com.sphereon.idk.*` group.

Core API:

- `lib-core-api-public`: `SessionContext`, `UserContext`, `IdentityResolutionResult`, `SessionContextFactory` SPI.
- `lib-core-api-default`: `DefaultSessionContextFactory`, `DefaultIdentityResolutionPipeline`, and the default session-manager plumbing.

JWT validation:

- `lib-oauth2-jwt-validation-api`: `JwtValidationService` interface, `JwtValidationConfig`, `IdpConfig`, `ValidatedAccessToken`, `ValidatedIdToken`, `JwtValidationError`.
- `lib-oauth2-jwt-validation-impl`: `DefaultJwtValidationService`, `DefaultIdpRegistry`.

Signature verification:

- `lib-oauth2-server-resource-public` and `lib-oauth2-server-resource-impl`: `VerifyJwtCommand` contract and implementation.
- `lib-crypto-core` and `lib-crypto-core-impl`: `JwtService` implementation that delegates to `VerifyJwsCommand` for signature operations.
- `lib-crypto-kms-provider-software`: software KMS provider that registers the signature algorithms (RS256, ES256, and so on) the validator dispatches to. Required even though BYO does not mint keys, because the verifier looks up algorithms through a registered KMS.

JWKS retrieval:

- `lib-data-link-http-client-impl`: HTTP client used by `JwksUrlExternalIdentifierResolutionServiceImpl` to fetch `.well-known/openid-configuration` and JWKS.
- `lib-oauth2-client-public` and `lib-oauth2-client-impl`: `FetchAuthorizationServerMetadataCommand` that reads the OIDC discovery document.

Ktor glue:

- `services/ktor/server/plugins/ktor-server-jwt-auth`: the `JwtAuthentication` plugin itself.
- `services/ktor/server/plugins/ktor-server-kotlin-inject`: DI bridge that exposes the IDK session graph to Ktor request handlers.

Dependency injection:

- `libs.bundles.app.platform.di`: Metro + kotlin-inject-anvil runtime.

No `com.sphereon.edk.*` or `com.sphereon.vdx.*` artifact should appear on the compile or runtime classpath. Enforce this with a `checkIdkPurity` Gradle task modeled on `examples/service-byo-oidc/build.gradle.kts`.

---

## Configuration surface

The validator reads its configuration from `JwtValidationConfig`:

| Field | Default | Notes |
|---|---|---|
| `enabled` | `true` | Set `false` in local development when you want to disable auth wholesale. |
| `defaultIdp` | `null` | The single upstream `IdpConfig`. Required for any production deployment. |
| `tenantIdps` | empty map | Static IDK startup configuration only. It is not a lifecycle authority and EDK does not extend it with a mutable tenant registry. |
| `anonymous` | `AnonymousAccessConfig(allowed=false)` | Opt-in list of path patterns that may be accessed without a token. |
| `strictIssuerMatching` | `true` | When `true`, an unknown issuer returns `Err(UntrustedIssuer)`. When `false`, unknown issuers fall back to `defaultIdp`. Keep `true` in production. |

`IdpConfig` carries:

| Field | Notes |
|---|---|
| `id` | Local identifier for this IdP (arbitrary). |
| `type` | `OIDC`, `KEYCLOAK`, `AZURE_AD`, `AUTH0`, `OKTA`, or `CUSTOM`. Influences discovery behaviour. |
| `issuer` | Expected `iss` claim, also the base URL used for OIDC discovery. |
| `audience` | Expected `aud` claim. |
| `jwksUri` | Direct JWKS URL. Optional; discovered via OIDC if not set. |
| `discoveryUri` | Optional override for the discovery endpoint. Defaults to `$issuer/.well-known/openid-configuration`. |
| `tenantClaim` | Name of the claim carrying the tenant id (for example `tenant_id`, `realm`, `tid`). IDK's default identity pipeline reads this when populating `SessionContext.tenant.tenantId`. |
| `tenantClaimAlternatives` | Fallback claim names tried in order. |

The Ktor plugin's configuration block (common case, uses the default lambdas that read services from `KotlinInjectPlugin`'s per-call graphs):

```kotlin
install(KotlinInjectPlugin) { appGraph = myAppGraph }
install(JwtAuthentication) {
    requireAuth = true
    anonymousPaths = listOf("/health", "/ready", "/api/v1/public/*")
    cookieName = null
    expectedAudience = System.getenv("AUTH_AUDIENCE")
}
```

When not using `KotlinInjectPlugin` (pure Koin, hand-wired, or tests), override the three resolver lambdas:

```kotlin
install(JwtAuthentication) {
    jwtValidationService = { myValidator }
    identityResolutionPipeline = { myPipeline }
    sessionContextFactory = { myFactory }
    requireAuth = true
}
```

On authentication failure the plugin responds with HTTP 401 and sets `WWW-Authenticate: Bearer error="invalid_token"` per RFC 6750.

---

## Wiring sketch

```kotlin
fun Application.module() {
    val jwtConfig = JwtValidationConfig(
        enabled = true,
        defaultIdp = IdpConfig.oidc(
            id = "primary",
            issuer = System.getenv("AUTH_IDP_ISSUER")
                ?: error("AUTH_IDP_ISSUER required"),
            audience = System.getenv("AUTH_AUDIENCE") ?: "byo-demo",
        ),
        strictIssuerMatching = true,
    )

    val appGraph = createGraphFactory<ByoOidcAppGraph.Factory>().create(jwtConfig)

    // Plugin order matters: KotlinInjectPlugin attaches the session graph
    // to each call before JwtAuthentication's onCall handler runs.
    install(KotlinInjectPlugin) {
        this.appGraph = appGraph
    }

    install(JwtAuthentication) {
        // jwtValidationService, identityResolutionPipeline, and
        // sessionContextFactory all default to reading from the kotlin-inject
        // graphs via call.getSessionService / call.getAppService.
        requireAuth = true
        anonymousPaths = listOf("/health", "/ready")
    }

    routing {
        get("/health") { call.respondText("ok") }
        get("/ready") { call.respondText("ok") }
        get("/api/v1/me") {
            val session = call.attributes[SessionContextAttributeKey]
            call.respond(
                mapOf(
                    "sessionId" to session.sessionId,
                    "tenantId" to session.context.tenant.tenantId,
                    "principal" to session.context.principal?.toString().orEmpty(),
                ),
            )
        }
    }
}
```

The session-scope/app-scope impedance is handled by the plugins themselves. `JwtValidationService` is `@SingleIn(SessionScope::class)` because its transitive dependencies (`VerifyJwtCommand` -> `JwtService` -> `VerifyJwsCommand` -> `IdentifierService`) need a live `SessionExecution`. `KotlinInjectPlugin` builds a per-request session graph at the `ApplicationCallPipeline.Plugins` phase; `JwtAuthentication`'s default `jwtValidationService` lambda resolves `call.getSessionService<JwtValidationService>()` at request time, which pulls a fresh session-scoped validator out of that graph. `IdentityResolutionPipeline` and `SessionContextFactory` are AppScope, resolved via `call.getAppService<T>()`. The `ktor-server-jwt-auth` module publishes two `@ContributesTo` extension graphs (`JwtAuthSessionExtensionGraph`, `JwtAuthAppExtensionGraph`) so Metro exposes the accessors on the merged graphs transparently, with no per-deployment wiring required.

---

## Anonymous paths

`anonymousPaths` supports glob-style patterns: literal paths, a trailing `*` to match any single segment, or `**` to match any number of segments. Common entries:

- `/health` and `/ready`: liveness and readiness probes.
- `/.well-known/*`: any public OIDC or security metadata the service exposes.
- `/api/v1/public/**`: a namespace for routes that do not require auth.

Do not place admin, tenant-scoped, or user-data routes behind an anonymous glob.

---

## Tenant resolution

IDK's default `OidcPrincipalResolver` reads the tenant id from the claim named in `IdpConfig.tenantClaim` (falling back to `tenantClaimAlternatives`, then to `client_id`). The resolved tenant is placed on `SessionContext.context.tenant.tenantId`.

A-6 moved tenant resolution entirely into `IdentityResolutionPipeline`, and FU-9 finished the migration by removing the `tenantId` field from both `ValidatedAccessToken` and `ValidatedIdToken`. The pipeline is the single source of truth; read the resolved tenant from `SessionContext.context.tenant.tenantId`. If you write your own resolver, add it to the pipeline's resolver chain — there is no longer a token-extraction path to confuse it with.

---

## Upgrade path to EDK

When any of the scope limits above becomes binding, switch to EDK. EDK administers hosted and external authorization servers as UUID-native tenant resources. It does not add a mutable identity-provider lifecycle registry above the IDK startup map.

EDK adds the following capabilities:

- Tenant-scoped hosted and external authorization-server resources under `/api/platform/config/v1/tenants/{tenantId}/authorization-servers`.
- Typed federation bindings from a hosted authorization server to an external OIDC authorization server.
- `FederationSessionStore` Postgres implementation: multi-replica-safe federation state.
- `AssurancePolicyResolver`: per-tenant `acr`/`amr` to LoA mapping with trust-chain gating.
- Hosted `LOCAL_ONLY`, `FEDERATED_ONLY`, and `HYBRID` authentication modes with deterministic upstream selection.
- Discovery validation, confidential-client references, and lifecycle operations through the authorization-server resource API.

Existing deployments are converted by the coded authorization-server authority migration. The retired tenant IdP tables are migration input only and are removed from ordinary runtime administration after successful conversion.

---

## Inter-service pattern: HTTP-over-RPC for session completion

Some deployments split authentication across more than one service. The Sphereon portal uses this topology: an STS (Security Token Service) handles federation and token issuance while a separate auth-bridge holds session state for the wallet and IDV flows. Putting the session store on both services would require distributed state or circular DI.

The pattern the portal uses, validated in production, is **HTTP-over-RPC completion**:

1. The STS drives the federation or IDV flow.
2. On completion, the STS makes a plain HTTP POST to the auth-bridge at a well-known completion endpoint, for example `POST /auth/oid4vp/sessions/{sessionId}/reconciliation/complete` with the extracted claims as JSON.
3. The auth-bridge owns the session store, writes the completion record, and responds with success.
4. The STS uses the response to decide the browser redirect.

The reference implementation is `services/service-sts/src/jvmMain/kotlin/com/sphereon/portal/sts/StsReconciliationHandler.kt` in the portal project.

When to use it:

- You have two or more services that need to share session state but want to avoid a shared database or a cross-service Metro graph.
- The services already expose HTTP endpoints and can authenticate each other cheaply (internal TLS, service-mesh auth, a shared secret, or a machine-to-machine token).
- The session-lifetime events are infrequent enough that an HTTP call is acceptable.

When not to use it:

- Every request needs the session state (then the HTTP-per-request cost dominates; share a session store instead).
- You need strong consistency across replicas (then back the session store with Postgres and let every replica read from it directly, as the EDK `FederationSessionStore` does).

---

## Testing the deployment

The reference test harness is at `examples/service-byo-oidc/src/test/kotlin/com/sphereon/example/byo/ByoOidcE2ETest.kt`. It boots Keycloak via testcontainers, imports a minimal realm, mints a token with the ROPC grant, and asserts that:

- A valid token reaches `/api/v1/me` and receives a 200 with the resolved principal.
- A missing token on a protected route returns 401 with the RFC 6750 header.
- An anonymous path returns 200 without auth.

The test skips gracefully if Docker is not available. In Docker 29.x environments, include `src/test/resources/docker-java.properties` with `api.version=1.44` to pin the API version.

---

## Checklist for a production BYO deployment

- Configure `defaultIdp` with production issuer, audience, and (recommended) an explicit JWKS URI to avoid a discovery round trip on cold start.
- Leave `strictIssuerMatching = true`.
- Register the software KMS provider so signature algorithms route correctly: `kms.providers.software.type=software`.
- Include the `checkIdkPurity` Gradle task in your CI pipeline.
- Expose `/health` and `/ready` in `anonymousPaths`.
- Log only non-sensitive claim metadata at info level. PII-bearing claims go to `trace` or are scrubbed.
- Monitor JWKS fetch latency; cache time-to-live is controlled by IDK's metadata resolver.

---

## Further reading

- Runnable example: `vdx/edk/idk/examples/service-byo-oidc/`.
- Action plan covering the full auth layer: `docs/reviews/2026-04-22-auth-layer/ACTION-PLAN.md`.
- Portal migration tracker (what portal and other pure-IDK consumers need to pick up): `docs/reviews/2026-04-22-auth-layer/PORTAL-MIGRATION.md`.
- Follow-up items surfaced during implementation: `docs/reviews/2026-04-22-auth-layer/FOLLOW-UPS.md`.
