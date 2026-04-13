# Identity Matching

The Identity Matching module maps external identifier hashes to internal identity references. It is crypto-agnostic — callers hash identifiers before calling.

---

## Overview

When a wallet-based authentication flow (e.g., OID4VP Auth Bridge) produces an external identifier such as a holder DID, public key, or email, the system needs to resolve it to an internal identity. Identity Matching provides this lookup and lifecycle management.

**Key principle:** The module stores and queries _hashes_ of identifiers, not raw values. Callers are responsible for hashing before calling. This keeps the module crypto-agnostic and avoids storing sensitive identifiers.

---

## Module Structure

```
lib/identity/matching/
├── public/    → lib-identity-matching-public (API + models)
└── impl/      → lib-identity-matching-impl   (InMemory store, commands, HTTP adapter)
```

**Package:** `com.sphereon.identity.matching`

---

## Core Model

### IdentityMatch

The central entity linking an external identifier hash to an internal identity.

```kotlin
data class IdentityMatch(
    val id: String,                          // Unique match ID (UUID)
    val identifierHash: String,              // Hash of the external identifier
    val identifierType: IdentifierType,      // Type discriminator
    val internalIdentityId: String,          // Reference to internal identity
    val tenantId: String,                    // Tenant isolation
    val metadata: Map<String, String>,       // Arbitrary key-value pairs
    val createdAt: Instant,
    val updatedAt: Instant?
)
```

### IdentifierType

An extensible value class with predefined constants:

| Type | Description |
|------|-------------|
| `KEY` | Public key hash |
| `DID` | DID identifier hash |
| `EMAIL` | Email hash |
| `SUBJECT_ID` | External subject ID hash |

Custom types are supported: `IdentifierType("BIOMETRIC_HASH")`.

### MatchResult

Sealed interface returned by lookup:

- `MatchResult.Found(match)` — match exists
- `MatchResult.NotFound` — no match for the given hash + type + tenant

---

## Commands

All commands follow IDK's `ServiceCommand` pattern with `TypedServiceCommandAdapter` implementations.

| Command | ID | Input → Output |
|---------|-----|----------------|
| `LookupIdentityMatchCommand` | `identity.matching.lookup` | `LookupIdentityMatchArgs → MatchResult` |
| `CreateIdentityMatchCommand` | `identity.matching.create` | `CreateIdentityMatchArgs → IdentityMatch` |
| `DeleteIdentityMatchCommand` | `identity.matching.delete` | `DeleteIdentityMatchArgs → Boolean` |
| `ListIdentityMatchesCommand` | `identity.matching.list` | `ListIdentityMatchesArgs → List<IdentityMatch>` |

### Lookup

```kotlin
val result = lookupCommand.execute(
    LookupIdentityMatchArgs(
        identifierHash = sha256(holderDid),
        identifierType = IdentifierType.DID,
        tenantId = "tenant-1"
    )
)

when (val matchResult = result.value) {
    is MatchResult.Found -> println("Resolved to: ${matchResult.match.internalIdentityId}")
    is MatchResult.NotFound -> println("No identity match — trigger reconciliation")
}
```

### Create

Returns `Err` with `DuplicateMatch` if a match already exists for the same hash + type + tenant.

```kotlin
val match = createCommand.execute(
    CreateIdentityMatchArgs(
        identifierHash = sha256(holderDid),
        identifierType = IdentifierType.DID,
        internalIdentityId = "user-456",
        tenantId = "tenant-1",
        metadata = mapOf("source" to "oid4vp", "verified_at" to "2025-01-15")
    )
)
```

---

## Store Interface

```kotlin
interface IdentityMatchStore {
    suspend fun findByIdentifierHash(tenantId: String, identifierHash: String, identifierType: IdentifierType): IdentityMatch?
    suspend fun findById(tenantId: String, matchId: String): IdentityMatch?
    suspend fun findByInternalIdentityId(tenantId: String, internalIdentityId: String): List<IdentityMatch>
    suspend fun create(match: IdentityMatch): IdentityMatch
    suspend fun delete(tenantId: String, matchId: String): Boolean
}
```

### Store Implementations

**IDK (in-memory, for development):**
- `InMemoryIdentityMatchStore` — `@SingleIn(AppScope)`, dual index (byId + byHash) for O(1) lookups

**EDK (persistent, for production):**
- `DatabaseIdentityMatchStore` — `@SingleIn(AppScope)`, uses `DatabaseRouter.getDriverForTenant(tenantId)` for tenant-scoped PostgreSQL routing. Replaces the in-memory store via `@ContributesBinding(replaces = [InMemoryIdentityMatchStore::class])`. Backed by SQLDelight-generated queries with `TIMESTAMP WITH TIME ZONE` columns and unique index on `(tenant_id, identifier_hash, identifier_type)`.

EDK persistence modules: `lib-data-store-identity-persistence-api` (bridge + repository interfaces) and `lib-data-store-identity-persistence-postgresql` (SQLDelight dialect implementation).

---

## HTTP API

Mount: `/identity/matching/v1`

| Method | Path | Command | Description |
|--------|------|---------|-------------|
| `POST` | `/matches/lookup` | Lookup | Find match by identifier hash |
| `POST` | `/matches` | Create | Create a new identity match |
| `DELETE` | `/matches/{matchId}?tenantId=` | Delete | Remove a match |
| `GET` | `/matches?identityId=&tenantId=` | List | List matches for an identity |

### POST /matches/lookup

```json
{
  "identifierHash": "abc123...",
  "identifierType": "DID",
  "tenantId": "tenant-1"
}
```

Response (200):
```json
{
  "type": "com.sphereon.identity.matching.model.MatchResult.Found",
  "match": {
    "id": "match-uuid",
    "identifierHash": "abc123...",
    "identifierType": "DID",
    "internalIdentityId": "user-456",
    "tenantId": "tenant-1",
    "metadata": {},
    "createdAt": "2025-01-15T10:30:00Z"
  }
}
```

---

## Error Types

| Error | Code | Description |
|-------|------|-------------|
| `MatchNotFound` | `identity_matching.match_not_found` | No match for the given ID or hash |
| `DuplicateMatch` | `identity_matching.duplicate_match` | Match already exists for this hash + type + tenant |
| `StoreError` | `identity_matching.store_error` | Storage operation failed |
| `InvalidIdentifier` | `identity_matching.invalid_identifier` | Identifier validation failed |

---

## DI Integration

**Bindings** (`IdentityMatchingCommandBindings`): `@ContributesTo(SessionScope)` — provides commands from `SessionScopedCommandRegistry`.

**Descriptors** (`IdentityMatchingCommandDescriptors`): `@ContributesTo(SessionScope)` — registers command implementations via `@IntoSet`.

**Store** (`InMemoryIdentityMatchStore`): `@SingleIn(AppScope)` / `@ContributesBinding(AppScope)`.

**HTTP Adapter** (`IdentityMatchingHttpAdapter`): `@ContributesBinding(SessionScope, HttpAdapter, multibinding=true)`.

To include in a service, add both modules as dependencies:

```kotlin
// In service's build.gradle.kts
implementation(projects.libIdentityMatchingPublic)
implementation(projects.libIdentityMatchingImpl)
```

DI auto-discovers all bindings via `@MergeComponent(AppScope)`.

---

## Dependencies

- `lib-core-api-public` — `ServiceCommand`, `IdkResult`, `IdkError`, `RoutedHttpAdapter`
- `kotlinx-serialization` — Model serialization
- `kotlinx-datetime` — Timestamps
- `kotlin-inject` + `kotlin-inject-anvil` — DI annotations
