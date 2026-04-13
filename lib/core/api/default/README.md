# IDK Core API Default Implementations

Default implementations for the IDK Core API interfaces. This module provides production-ready implementations that can be used out of the box or replaced with custom implementations.

## Features

- **Caching** - In-memory cache backend using Kache library
- **HTTP** - Default HTTP adapter catalog, dispatcher, and JSON codec
- **Configuration** - Properties file-based configuration sources
- **Context** - User context and session management implementations
- **Logging** - Default log manager implementations

## Cache Backend

### KacheCacheBackend

High-performance in-memory cache using the [Kache](https://github.com/AhmadMayo/Kache) library.

**Features:**
- LRU eviction strategy
- TTL support with manual expiration checking
- Pattern-based key operations
- Kotlin Multiplatform (JVM, JS, iOS, Linux)

**Configuration:**

```yaml
cache:
  defaults:
    max-entries: 10000      # Maximum entries before LRU eviction
    ttl:
      app: 10m              # App-scope TTL
      tenant: 5m            # Tenant-scope TTL
      principal: 2m         # Principal-scope TTL
```

**Usage:**

The KacheCacheBackend is automatically registered via DI. No manual setup required.

```kotlin
// Inject CacheService - it will use KacheCacheBackend by default
@Inject
class MyService(private val cacheService: CacheService) {

    suspend fun getCachedData(key: String): Data? {
        return cacheService.get<String, Data>(
            CacheGetArgs.app("my-namespace", key)
        ).getOrNull()?.value
    }
}
```

**Manual Access (Advanced):**

```kotlin
@Inject
class AdvancedService(private val kacheBackend: KacheCacheBackend) {

    suspend fun cleanup() {
        // Manually trigger cleanup of expired entries
        val cleaned = kacheBackend.cleanupExpired()
        println("Cleaned $cleaned expired entries")
    }
}
```

### Backend Capabilities

| Capability | Value |
|------------|-------|
| `isLocal` | `true` |
| `isDistributed` | `false` |
| `supportsTtl` | `true` |
| `supportsPatternDelete` | `true` |
| `supportsBatchOps` | `true` |
| `isPersistent` | `false` |

### Pattern Matching

The backend supports glob-style patterns for key operations:

```kotlin
// Delete all config keys starting with "db."
backend.deleteByPattern("config::APP::::db.*")

// Find all keys matching a pattern
val keys = backend.keys("oauth-tokens::PRINCIPAL::tenant-*::user-*::*")
```

## HTTP Components

### DefaultHttpAdapterCatalog

Registry for HTTP adapters. Automatically collects adapters contributed via DI.

### DefaultHttpAdapterDispatcher

Dispatches HTTP requests to appropriate adapters based on path matching.

### JsonHttpBodyCodec

JSON serialization/deserialization for HTTP request/response bodies using kotlinx.serialization.

## Configuration Components

### PropertiesFileAppPropertySource

Loads application-level properties from:
- `application.properties`
- `application-{profile}.properties`

### PropertiesFileTenantPropertySource

Loads tenant-specific properties from:
- `tenants/{tenantId}/application.properties`

### PropertiesFilePrincipalPropertySource

Loads principal-specific properties from:
- `tenants/{tenantId}/principals/{principalId}/application.properties`

## Context Components

### UserContextManagerImpl

Manages user context lifecycle, including tenant and principal resolution.

### SessionContextManagerImpl

Manages session context lifecycle within a user context.

### CrossContextOperationsImpl

Handles operations that span multiple contexts (e.g., cross-tenant operations).

## Logging Components

### AppLogManagerImpl

Application-scope log manager with configurable log levels.

### SessionLogManagerImpl

Session-scope log manager that includes session context in log entries.

## Module Structure

```
lib-core-api-default/
├── src/
│   └── commonMain/kotlin/com/sphereon/
│       ├── core/api/
│       │   ├── cache/
│       │   │   ├── KacheCacheBackend.kt    # In-memory cache
│       │   │   └── KacheCacheModule.kt     # DI contribution
│       │   └── http/
│       │       ├── codec/
│       │       │   ├── DefaultHttpBodyCodecRegistry.kt
│       │       │   └── JsonHttpBodyCodec.kt
│       │       ├── describe/
│       │       │   └── NoOpAdapterDescriptorProvider.kt
│       │       └── dispatch/
│       │           ├── DefaultHttpAdapterCatalog.kt
│       │           ├── DefaultHttpAdapterDispatcher.kt
│       │           └── NoOpHttpAdapter.kt
│       └── core/defaults/
│           ├── app/
│           │   ├── AppImpl.kt
│           │   └── RootScopeProviderImpl.kt
│           ├── conf/
│           │   ├── AbstractConfigEnvironment.kt
│           │   ├── AppConfigEnvironmentImpl.kt
│           │   ├── TenantConfigEnvironmentImpl.kt
│           │   ├── PrincipalConfigEnvironmentImpl.kt
│           │   └── PropertiesFile*PropertySourceImpl.kt
│           ├── context/
│           │   ├── UserContextManagerImpl.kt
│           │   ├── UserContextImpl.kt
│           │   ├── CrossContextOperationsImpl.kt
│           │   └── AnonymousUserComponentManagerImpl.kt
│           ├── session/
│           │   ├── SessionContextManagerImpl.kt
│           │   ├── SessionContextImpl.kt
│           │   └── SessionInstanceImpl.kt
│           └── log/
│               ├── AppLogManagerImpl.kt
│               ├── SessionLogManagerImpl.kt
│               └── NoLogger.kt
```

## Replacing Default Implementations

Use kotlin-inject-anvil's `replaces` parameter to substitute implementations:

```kotlin
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(
    AppScope::class,
    boundType = CacheBackend::class,
    replaces = [KacheCacheBackend::class]
)
class MyCacheBackend : CacheBackend {
    // Custom implementation
}
```

## Dependencies

This module requires:
- `lib-core-api-public` - Core API interfaces
- `com.mayakapps.kache:kache` - In-memory cache library
- `org.jetbrains.kotlinx:kotlinx-serialization-json` - JSON serialization

## Testing

Test utilities for default implementations:

```kotlin
@Test
fun testKacheCacheBackend() = runTest {
    val backend = KacheCacheBackend(maxSize = 100)

    backend.set("key1", "value1".encodeToByteArray(), ttlMs = 60_000)

    val result = backend.get("key1")
    assertEquals("value1", result?.decodeToString())
}
```

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

© 2026 Sphereon International B.V.
