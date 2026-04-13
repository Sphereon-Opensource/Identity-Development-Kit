# Identity Reconciliation

The Identity Reconciliation module orchestrates OIDC-based verification with an external Identity Provider to establish links between external identifiers and internal identities.

---

## Overview

When Identity Matching returns `NotFound`, the system needs a way to establish a new link. Identity Reconciliation provides this by:

1. Creating a reconciliation session with an OIDC authorization URL
2. Redirecting the user to an external IdP for authentication
3. Upon callback, completing the flow by creating an identity match

This is a generic OIDC RP (Relying Party) reconciliation flow — it works with any OIDC-compliant IdP.

```
                           ┌──────────────┐
   Wallet Auth ──────────► │  Identity    │ ── Found ──► Internal Identity
   (OID4VP)                │  Matching    │
                           └──────┬───────┘
                                  │ NotFound
                                  ▼
                           ┌──────────────┐
                           │  Identity    │ ── Create Session ──► OIDC AuthZ URL
                           │  Reconcil.   │
                           └──────┬───────┘
                                  │ Callback (auth code)
                                  ▼
                           ┌──────────────┐
                           │  Complete    │ ── Creates ──► IdentityMatch
                           │  Reconcil.   │
                           └──────────────┘
```

---

## Module Structure

```
lib/identity/reconciliation/
├── public/    → lib-identity-reconciliation-public (API + models)
└── impl/      → lib-identity-reconciliation-impl   (stores, commands, HTTP adapter)
```

**Package:** `com.sphereon.identity.reconciliation`

---

## Core Models

### ReconciliationSession

Tracks the state of an OIDC reconciliation flow.

```kotlin
data class ReconciliationSession(
    val id: String,
    val tenantId: String,
    val status: ReconciliationSessionStatus,
    val identifierHash: String,           // Hash of the external identifier to link
    val identifierType: IdentifierType,   // From identity-matching module
    val providerId: String,               // Reference to ReconciliationProvider
    val authorizationUrl: String?,        // OIDC authorization URL
    val state: String?,                   // OIDC state parameter
    val nonce: String?,                   // OIDC nonce
    val codeVerifier: String?,            // PKCE code verifier
    val redirectUri: String?,             // Callback URI
    val tokenEndpoint: String?,           // Cached token endpoint (from OIDC discovery or provider config)
    val resolvedIdentity: ResolvedIdentity?,
    val errorMessage: String?,
    val createdAt: Instant,
    val expiresAt: Instant                // Sessions expire after 10 minutes
)
```

### ReconciliationSessionStatus

```
CREATED → REDIRECTED → CALLBACK_RECEIVED → COMPLETED
                                         → ERROR
                     → EXPIRED
                     → CANCELLED
```

| Status | Description |
|--------|-------------|
| `CREATED` | Session created, authorization URL generated |
| `REDIRECTED` | User redirected to IdP |
| `CALLBACK_RECEIVED` | Auth code received from IdP |
| `COMPLETED` | Identity match created successfully |
| `EXPIRED` | Session timed out (10 min default) |
| `CANCELLED` | Explicitly cancelled |
| `ERROR` | Flow failed |

### ReconciliationProvider

Configuration for an external OIDC Identity Provider.

```kotlin
data class ReconciliationProvider(
    val id: String,
    val name: String,
    val issuerUrl: String,                         // OIDC issuer URL
    val clientId: String,
    val clientSecret: String?,
    val scopes: List<String> = listOf("openid"),
    val claimMappings: Map<String, String>,         // IdP claim → internal claim
    val identifierClaimName: String = "sub",        // Which claim identifies the user
    val enabled: Boolean = true,
    val authorizationEndpointOverride: String?,     // Override OIDC Discovery for auth endpoint
    val tokenEndpointOverride: String?,             // Override OIDC Discovery for token endpoint
)
```

Endpoint overrides allow administrators to explicitly configure endpoints for non-standard OIDC providers. When set, they take priority over OIDC Discovery and convention-based fallback.

### ResolvedIdentity

The identity resolved from the external IdP after OIDC flow completion.

```kotlin
data class ResolvedIdentity(
    val externalSubject: String,         // Subject from IdP
    val externalIssuer: String,          // Issuer URL
    val claims: Map<String, JsonElement>, // Type-preserving claims (nested objects, numbers, booleans)
    val internalIdentityId: String?      // Linked internal identity
)
```

---

## Commands

| Command | ID | Input → Output |
|---------|-----|----------------|
| `CreateReconciliationSessionCommand` | `identity.reconciliation.create` | `CreateReconciliationSessionArgs → CreateReconciliationSessionResult` |
| `CompleteReconciliationCommand` | `identity.reconciliation.complete` | `CompleteReconciliationArgs → CompleteReconciliationResult` |
| `GetReconciliationSessionCommand` | `identity.reconciliation.get` | `GetReconciliationSessionArgs → ReconciliationSession` |
| `CancelReconciliationSessionCommand` | `identity.reconciliation.cancel` | `CancelReconciliationSessionArgs → ReconciliationSession` |

### Create Session

Looks up the provider, generates PKCE + state + nonce, builds an OIDC authorization URL, and stores the session.

```kotlin
val result = createSessionCommand.execute(
    CreateReconciliationSessionArgs(
        identifierHash = sha256(holderDid),
        identifierType = IdentifierType.DID,
        providerId = "keycloak-prod",
        tenantId = "tenant-1",
        redirectUri = "https://app.example.com/reconciliation/callback"
    )
)

val (session, authorizationUrl) = result.value
// Redirect user to authorizationUrl
```

### Complete Reconciliation

Called after the IdP callback. Validates the state parameter, then creates an `IdentityMatch` via the matching module.

```kotlin
val result = completeCommand.execute(
    CompleteReconciliationArgs(
        sessionId = "sess-123",
        tenantId = "tenant-1",
        authorizationCode = "auth-code-from-idp",
        state = "state-from-callback",
        internalIdentityId = "user-789"
    )
)

val (session, match) = result.value
// session.status == COMPLETED
// match.internalIdentityId == "user-789"
```

---

## Store Interfaces

### ReconciliationSessionStore

```kotlin
interface ReconciliationSessionStore {
    suspend fun findById(tenantId: String, sessionId: String): ReconciliationSession?
    suspend fun findByState(tenantId: String, state: String): ReconciliationSession?
    suspend fun create(session: ReconciliationSession): ReconciliationSession
    suspend fun update(session: ReconciliationSession): ReconciliationSession
    suspend fun delete(tenantId: String, sessionId: String): Boolean
    suspend fun findExpired(tenantId: String, cutoff: Instant): List<ReconciliationSession>
}
```

All methods take `tenantId` to enable tenant-scoped database routing in persistent implementations.

### ReconciliationProviderStore

```kotlin
interface ReconciliationProviderStore {
    suspend fun findById(providerId: String): ReconciliationProvider?
    suspend fun findAll(): List<ReconciliationProvider>
    suspend fun save(provider: ReconciliationProvider): ReconciliationProvider
    suspend fun delete(providerId: String): Boolean
}
```

### Store Implementations

**IDK (in-memory, for development):**
- `InMemoryReconciliationSessionStore` — `@SingleIn(AppScope)`, state index for OAuth callbacks
- `InMemoryReconciliationProviderStore` — `@SingleIn(AppScope)`

**EDK (persistent, for production):**
- `DatabaseReconciliationSessionStore` — `@SingleIn(AppScope)`, uses `DatabaseRouter.getDriverForTenant(tenantId)` for tenant-scoped PostgreSQL routing. Replaces `InMemoryReconciliationSessionStore` via `@ContributesBinding(replaces = [...])`
- `ConfigBackedReconciliationProviderStore` — `@SingleIn(AppScope)`, loads provider configuration from `AppConfigService` via `ConfigBinder.getConfigMap<ReconciliationProvider>("reconciliation.providers")`. Provider config (issuer URL, client ID, client secret, scopes) is static deployment configuration with secrets support (`${env:...}` interpolation). Replaces `InMemoryReconciliationProviderStore`.

EDK persistence modules: `lib-data-store-identity-persistence-api` (bridge + repository interfaces) and `lib-data-store-identity-persistence-postgresql` (SQLDelight dialect implementation).

---

## Session Cleanup

The `ReconciliationSessionCleanupJob` periodically marks expired sessions as `EXPIRED`:

```kotlin
class ReconciliationSessionCleanupJob(
    private val sessionStore: ReconciliationSessionStore,
    private val tenantIdProvider: suspend () -> List<String>
)
```

- Runs on a configurable interval (default 5 minutes)
- Iterates tenants via `tenantIdProvider` (injected by the service layer)
- Finds sessions past their `expiresAt` and updates status to `EXPIRED`
- Best-effort — exceptions are caught and logged

---

## HTTP API

Mount: `/identity/reconciliation/v1`

| Method | Path | Command | Description |
|--------|------|---------|-------------|
| `POST` | `/sessions` | Create | Start reconciliation, get auth URL |
| `GET` | `/sessions/{sessionId}?tenantId=` | Get | Check session status |
| `POST` | `/sessions/{sessionId}/complete` | Complete | Finish with auth code |
| `DELETE` | `/sessions/{sessionId}?tenantId=` | Cancel | Cancel a session |

### POST /sessions

```json
{
  "identifierHash": "abc123...",
  "identifierType": "DID",
  "providerId": "keycloak-prod",
  "tenantId": "tenant-1",
  "redirectUri": "https://app.example.com/callback"
}
```

Response (201):
```json
{
  "session": {
    "id": "sess-uuid",
    "status": "CREATED",
    "authorizationUrl": "https://idp.example.com/authorize?response_type=code&client_id=..."
  },
  "authorizationUrl": "https://idp.example.com/authorize?response_type=code&client_id=..."
}
```

### POST /sessions/{sessionId}/complete

```json
{
  "sessionId": "sess-uuid",
  "tenantId": "tenant-1",
  "authorizationCode": "auth-code-from-callback",
  "state": "state-from-callback",
  "internalIdentityId": "user-789"
}
```

Response (200):
```json
{
  "session": { "id": "sess-uuid", "status": "COMPLETED", "..." },
  "match": { "id": "match-uuid", "identifierHash": "abc123...", "internalIdentityId": "user-789" }
}
```

---

## Error Types

| Error | Code | Description |
|-------|------|-------------|
| `SessionNotFound` | `identity_reconciliation.session_not_found` | Session ID not found |
| `SessionExpired` | `identity_reconciliation.session_expired` | Session timed out |
| `SessionInvalidState` | `identity_reconciliation.session_invalid_state` | Wrong status for operation |
| `ProviderNotFound` | `identity_reconciliation.provider_not_found` | Provider ID not found |
| `OidcFlowFailed` | `identity_reconciliation.oidc_flow_failed` | OIDC authorization failed |
| `TokenExchangeFailed` | `identity_reconciliation.token_exchange_failed` | Token exchange failed |
| `StateMismatch` | `identity_reconciliation.state_mismatch` | OIDC state doesn't match |
| `MatchCreationFailed` | `identity_reconciliation.match_creation_failed` | Creating identity match failed |

---

## Integration with Identity Matching

The reconciliation module depends on `lib-identity-matching-public` for:

- `IdentifierType` — shared type discriminator
- `IdentityMatch` — returned as part of `CompleteReconciliationResult`
- `CreateIdentityMatchCommand` — called by `CompleteReconciliationCommandImpl` to create the match

This ensures the matching store is the single source of truth for all identity links, whether created manually or via reconciliation.

---

## DI Integration

To include in a service, add all four modules:

```kotlin
// In service's build.gradle.kts
implementation(projects.libIdentityMatchingPublic)
implementation(projects.libIdentityMatchingImpl)
implementation(projects.libIdentityReconciliationPublic)
implementation(projects.libIdentityReconciliationImpl)
```

DI auto-discovers all bindings via `@MergeComponent(AppScope)`.

---

## Dependencies

- `lib-identity-matching-public` — `IdentityMatch`, `IdentifierType`, `CreateIdentityMatchCommand`
- `lib-core-api-public` — `ServiceCommand`, `IdkResult`, `IdkError`, `RoutedHttpAdapter`
- `lib-oauth2-common-public` — `OidcTokenClaimExtractor` (used by `CompleteReconciliationCommandImpl` for ID token claim extraction)
- `lib-oauth2-client-public` — `CreatePkceCommand`, `ExchangeTokenCommand` (PKCE and token exchange)
- `kotlinx-serialization` — Model serialization
- `kotlinx-datetime` — Timestamps and expiry
- `kotlin-inject` + `kotlin-inject-anvil` — DI annotations
