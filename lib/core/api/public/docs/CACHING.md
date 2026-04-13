<!--
  Copyright 2025 Sphereon International B.V.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Caching Infrastructure

The IDK provides a unified caching layer that supports multiple backends, multi-tenant key partitioning, and config-driven backend selection.

For canonical cache API entry points and transitional paths, see [API-SURFACE.md](./API-SURFACE.md).

## Overview

### Canonical API Choice

For new integration code, prefer `CacheService` and `TypedCacheService`.  
Use `CacheAccessor` only when you explicitly want scope accessor ergonomics over `IdkResult`-wrapped service operations.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CacheManager (AppScope)                         │
│  - Registers available backends                                         │
│  - Selects backend(s) based on CacheRequirements                       │
│  - Creates caches with proper namespace partitioning                    │
└────────────────────────────────────────────────────────────────────────┬┘
                                                                         │
        ┌────────────────────────────┬───────────────────────────────────┤
        ▼                            ▼                                   ▼
┌───────────────────┐    ┌───────────────────┐              ┌───────────────────┐
│ KacheCacheBackend │    │ RestCacheBackend  │              │ CustomKVBackend   │
│   (in-memory)     │    │  (distributed)    │              │   (pluggable)     │
│   Always present  │    │   Optional        │              │                   │
└───────────────────┘    └───────────────────┘              └───────────────────┘
```

## Quick Start

### Basic Usage

```kotlin
@Inject
class MyService(private val cacheService: CacheService) {

    suspend fun getData(key: String): MyData? {
        return cacheService.get<String, MyData>(
            CacheGetArgs.app("my-namespace", key)
        ).getOrNull()?.value
    }

    suspend fun saveData(key: String, data: MyData) {
        cacheService.put(
            CachePutArgs.app("my-namespace", key, data, ttl = 10.minutes)
        )
    }
}
```

### Multi-Tenant Caching

```kotlin
// Tenant-scoped cache entry
cacheService.put(
    CachePutArgs.tenant("settings", tenantId = "acme-corp", key = "theme", value = "dark")
)

// Principal-scoped cache entry (user within tenant)
cacheService.put(
    CachePutArgs.principal(
        namespace = "tokens",
        tenantId = "acme-corp",
        principalId = "user-123",
        key = "access-token",
        value = tokenData
    )
)
```

## Key Concepts

### Cache Scopes

Keys are partitioned by scope for multi-tenant isolation:

| Scope | Description | Key Format |
|-------|-------------|------------|
| `APP` | Application-wide, shared across all tenants | `namespace::APP::::key` |
| `TENANT` | Tenant-specific data | `namespace::TENANT::tenantId::key` |
| `PRINCIPAL` | User-specific data within a tenant | `namespace::PRINCIPAL::tenantId::principalId::key` |

### Namespaces

Each module/subsystem should use its own namespace to avoid key collisions:

```kotlin
// Config module
CacheGetArgs.app("config", "db.pool.size")

// DID resolver
CacheGetArgs.app("did-resolver", "did:web:example.com")

// OAuth tokens
CacheGetArgs.principal("oauth-tokens", tenantId, userId, "access-token")
```

### Cache Locality

Cache requirements specify where data should be stored:

| Locality | Description | Use Case |
|----------|-------------|----------|
| `LOCAL_ONLY` | In-memory only, fastest | Config, static data |
| `LOCAL_PREFERRED` | Local with optional distributed fallback | Most use cases |
| `DISTRIBUTED_PREFERRED` | Distributed with local fallback | Shared state |
| `DISTRIBUTED_ONLY` | Must be distributed | Session tokens in multi-instance |

## Configuration

### Application Properties

```yaml
cache:
  # Global defaults
  defaults:
    locality: LOCAL_PREFERRED
    distributed-fallback: true
    write-through: false
    max-entries: 10000
    ttl:
      app: 10m
      tenant: 5m
      principal: 2m

  # Per-namespace overrides
  namespaces:
    config:
      ttl:
        app: 30m

    did-resolver:
      locality: LOCAL_PREFERRED
      distributed-fallback: true
      ttl:
        app: 1h

    oauth-tokens:
      locality: DISTRIBUTED_ONLY
      ttl:
        principal: 15m

    user-preferences:
      ttl:
        tenant: 10m
        principal: 5m
```

### Environment Variables

Override configuration via environment variables:

```bash
# Global defaults
export CACHE_DEFAULTS_LOCALITY=DISTRIBUTED_PREFERRED
export CACHE_DEFAULTS_TTL_APP=15m

# Per-namespace
export CACHE_NAMESPACES_OAUTH_TOKENS_LOCALITY=DISTRIBUTED_ONLY
export CACHE_NAMESPACES_CONFIG_TTL_APP=30m
```

## API Reference

### CacheService

High-level service for cache operations:

```kotlin
interface CacheService {
    /** Get a value from cache */
    suspend fun <K : Any, V : Any> get(args: CacheGetArgs<K>): IdkResult<CacheGetResult<V>, IdkError>

    /** Put a value into cache */
    suspend fun <K : Any, V : Any> put(args: CachePutArgs<K, V>): IdkResult<CachePutResult, IdkError>

    /** Remove a value from cache */
    suspend fun <K : Any> remove(args: CacheRemoveArgs<K>): IdkResult<CacheRemoveResult, IdkError>

    /** Invalidate cache entries */
    suspend fun invalidate(args: CacheInvalidateArgs): IdkResult<CacheInvalidateResult, IdkError>

    /** Get cache statistics */
    fun stats(): Map<String, CacheStatistics>
}
```

### CacheGetArgs

```kotlin
// App-scoped
val args = CacheGetArgs.app("namespace", "key")

// Tenant-scoped
val args = CacheGetArgs.tenant("namespace", tenantId = "tenant-1", key = "key")

// Principal-scoped
val args = CacheGetArgs.principal("namespace", tenantId = "tenant-1", principalId = "user-1", key = "key")
```

### CachePutArgs

```kotlin
// With TTL
val args = CachePutArgs.app("namespace", "key", value, ttl = 5.minutes)

// Tenant-scoped with TTL
val args = CachePutArgs.tenant("namespace", "tenant-1", "key", value, ttl = 10.minutes)
```

### CacheInvalidateArgs

```kotlin
// Invalidate all entries in a namespace
val args = CacheInvalidateArgs.namespace("config")

// Invalidate all entries for a tenant (across all namespaces)
val args = CacheInvalidateArgs.tenant("tenant-1")

// Invalidate by key pattern within a namespace
val args = CacheInvalidateArgs.pattern("config", "db.*")
```

## Direct Cache Access

For advanced use cases, access the `CacheManager` directly:

```kotlin
@Inject
class AdvancedService(private val cacheManager: CacheManager) {

    private val cache: ScopedCache<String, MyData> by lazy {
        cacheManager.createStringCache(
            requirements = CacheRequirements(
                namespace = "my-advanced-cache",
                locality = CacheLocality.LOCAL_PREFERRED,
                distributedFallback = true,
                ttlConfig = CacheTtlConfig(
                    app = 10.minutes,
                    tenant = 5.minutes,
                    principal = 2.minutes
                )
            ),
            valueSerializer = CacheSerializers.json<MyData>()
        )
    }

    suspend fun getAppData(key: String): MyData? = cache.getApp(key)

    suspend fun getTenantData(tenantId: String, key: String): MyData? =
        cache.getTenant(tenantId, key)
}
```

## Cache Backends

### Built-in: KacheCacheBackend

The default in-memory cache backend using the [Kache](https://github.com/AhmadMayo/Kache) library:

- **Location**: `lib-core-api-default`
- **Strategy**: LRU eviction
- **Features**: TTL support, pattern-based deletion, batch operations
- **Platforms**: JVM, JS, iOS, Linux

### Adding Custom Backends

Implement `CacheBackend` and contribute via DI:

```kotlin
@Inject
@SingleIn(AppScope::class)
class RedisCacheBackend(private val redisClient: RedisClient) : CacheBackend {

    override val id = "redis"

    override val capabilities = BackendCapabilities(
        isLocal = false,
        isDistributed = true,
        supportsTtl = true,
        supportsPatternDelete = true,
        supportsBatchOps = true,
        isPersistent = false
    )

    override suspend fun get(key: String): ByteArray? {
        return redisClient.get(key)?.encodeToByteArray()
    }

    override suspend fun set(key: String, value: ByteArray, ttlMs: Long?) {
        if (ttlMs != null) {
            redisClient.setex(key, ttlMs / 1000, value.decodeToString())
        } else {
            redisClient.set(key, value.decodeToString())
        }
    }

    // ... implement remaining methods
}

// DI module to contribute the backend
@ContributesTo(AppScope::class)
interface RedisCacheModule {
    @Provides
    @IntoSet
    fun provideRedisCacheBackend(backend: RedisCacheBackend): CacheBackend = backend
}
```

## Layered Caching

When both local and distributed backends are available and requirements specify fallback, the `LayeredCacheBackend` provides:

- **Read-through**: Check local first, then distributed, populate local on hit
- **Write-through**: Write to both local and distributed (when enabled)

```yaml
cache:
  namespaces:
    sessions:
      locality: LOCAL_PREFERRED
      distributed-fallback: true
      write-through: true  # Writes go to both local and distributed
```

## Cache Statistics

Monitor cache performance:

```kotlin
val stats = cacheService.stats()

stats.forEach { (namespace, stat) ->
    println("$namespace: hits=${stat.hits}, misses=${stat.misses}, hitRate=${stat.hitRate}")
    println("  size=${stat.size}/${stat.maxSize}, utilization=${stat.utilization}")
}
```

## Best Practices

### 1. Choose Appropriate Scopes

```kotlin
// Global config - APP scope
CacheGetArgs.app("config", "feature.enabled")

// Tenant settings - TENANT scope
CacheGetArgs.tenant("settings", tenantId, "theme")

// User tokens - PRINCIPAL scope
CacheGetArgs.principal("tokens", tenantId, userId, "access-token")
```

### 2. Set Reasonable TTLs

```kotlin
// Static data - longer TTL
CachePutArgs.app("config", key, value, ttl = 30.minutes)

// Session data - shorter TTL
CachePutArgs.principal("sessions", tenantId, userId, sessionId, session, ttl = 15.minutes)
```

### 3. Invalidate on Updates

```kotlin
suspend fun updateTenantSettings(tenantId: String, settings: Settings) {
    // Update database
    repository.save(tenantId, settings)

    // Invalidate cache
    cacheService.invalidate(CacheInvalidateArgs.tenant(tenantId))
}
```

### 4. Handle Cache Misses Gracefully

```kotlin
suspend fun getConfig(key: String): Config {
    val cached = cacheService.get<String, Config>(CacheGetArgs.app("config", key))
        .getOrNull()?.value

    if (cached != null) return cached

    // Load from source and cache
    val config = configRepository.load(key)
    cacheService.put(CachePutArgs.app("config", key, config, ttl = 10.minutes))
    return config
}
```

### 5. Use Config for Locality Decisions

Let deployment configuration determine locality, not code:

```kotlin
// Code only declares namespace - locality comes from config
cacheManager.registerNamespace(
    CacheRequirements(namespace = "my-cache")  // Defaults overridden by config
)
```

## Troubleshooting

### Cache Not Being Used

1. Check that the namespace is registered
2. Verify TTL hasn't expired
3. Check logs for backend health issues

### Stale Data

1. Reduce TTL in configuration
2. Call `invalidate()` after updates
3. Enable write-through for distributed consistency

### Memory Issues

1. Reduce `max-entries` in configuration
2. Use shorter TTLs for large objects
3. Check cache statistics for utilization

## See Also

- [CONFIGURATION.md](CONFIGURATION.md) - Configuration system overview
- [CORE-CONCEPTS.md](CORE-CONCEPTS.md) - Core IDK concepts
- `lib-core-api-default` - KacheCacheBackend implementation
