# IDK Configuration System

Comprehensive guide to the Identity Development Kit (IDK) configuration system, providing hierarchical, multiplatform configuration management with support for multiple property sources, profiles, typed configuration binding, and secret management.

This guide is written for developers building on IDK who need to define configurable components, retrieve configuration values, and integrate with the secret management system.

For canonical configuration API entry points and transitional paths, see [API-SURFACE.md](./API-SURFACE.md).

## Table of Contents

1. [Quick Start](#quick-start)
2. [Overview](#overview)
3. [Retrieving Configuration Values](#retrieving-configuration-values)
4. [Typed Configuration Binding](#typed-configuration-binding)
5. [Polymorphic Configuration Binding](#polymorphic-configuration-binding)
6. [Creating Configurable Components](#creating-configurable-components)
7. [Configuration Hierarchy](#configuration-hierarchy)
8. [Property Sources](#property-sources)
9. [Property Resolution](#property-resolution)
10. [Property File Support](#property-file-support)
11. [Key Normalization](#key-normalization)
12. [Profile-Based Configuration](#profile-based-configuration)
13. [Property Interpolation](#property-interpolation)
14. [Secret Management](#secret-management)
15. [Property Protection](#property-protection)
16. [Extending the Configuration System](#extending-the-configuration-system)
17. [Caching](#caching)
18. [Dependency Injection Integration](#dependency-injection-integration)
19. [Troubleshooting](#troubleshooting)
20. [Best Practices](#best-practices)
21. [API Reference](#api-reference)

---

## Quick Start

### Injecting Configuration

The simplest way to access configuration is by injecting `AppConfigService`:

```kotlin
@Inject
class MyService(
    private val configService: AppConfigService
) {
    fun getSettings() {
        // Get a string property with default
        val apiUrl = configService.getPropertyAsString("api.url", "http://localhost:8080")

        // Properties files return strings; parse primitives yourself
        val timeout = configService.getPropertyAsString("http.timeout")?.toIntOrNull() ?: 5000

        // Get required property (throws if missing)
        val apiKey = configService.getRequiredPropertyAsString("api.key")

        // Check if property exists
        if (configService.containsProperty("feature.enabled")) {
            val enabled = configService.getPropertyAsString("feature.enabled")?.toBoolean() ?: false
        }
    }
}
```

### Binding Configuration to a Data Class

For structured configuration, define a `@Serializable` data class and use `ConfigBinder`:

```kotlin
@Serializable
data class DatabaseConfig(
    val host: String,
    val port: Int = 5432,
    val name: String,
    val poolSize: Int = 10
)

@Inject
class DatabaseService(configService: AppConfigService) {
    // Bind properties with "database." prefix to typed object
    private val config: DatabaseConfig = configService.toConfigBinder()
        .getRequiredConfig<DatabaseConfig>("database")
}
```

### Property File Setup

```properties
# config/application.properties
database.host=localhost
database.port=5432
database.name=myapp
database.pool.size=10

http.timeout=5000
api.url=https://api.example.com
```

---

## Overview

The IDK configuration system provides a flexible, hierarchical approach to configuration management that supports:

- **Multi-tenant isolation** - Separate configuration per tenant and principal
- **Profile-based overrides** - Environment-specific configuration (dev, staging, production)
- **Multiple property sources** - Environment variables (core), property files (default module), settings storage and database/cloud providers (EDK modules)
- **Typed configuration binding** - Bind flat properties to data classes using kotlinx.serialization
- **Polymorphic configuration binding** - Bind discriminator-based config entries to sealed class hierarchies
- **Automatic key normalization** - Consistent key access regardless of naming convention
- **Property interpolation** - Reference other properties within values (enabled when the pipeline module is on the classpath)
- **Secret management** - Secret providers (env provider built in; external providers are optional)
- **Multiplatform support** - Works on JVM, JS, iOS, and Native targets

### Canonical API Choice

For application/service code, prefer scoped `ConfigService` injection (`AppConfigService`, `TenantConfigService`, `PrincipalConfigService`).
Use direct `PropertyResolver` interfaces mainly for infrastructure components and adapter internals.

### Architecture Overview

```
ConfigService (App -> Tenant -> Principal)
  -> ConfigEnvironment
      -> PropertySources (env, files, settings, database/cloud, defaults)
```

---

## Retrieving Configuration Values

There are three levels of configuration access, from simplest to most powerful.

### Level 1: Individual Property Access via ConfigService

`ConfigService` extends `PropertyResolver` and is the standard injection target:

```kotlin
@Inject
class MyService(private val config: AppConfigService) {

    // --- String access (safest for property-file sources) ---
    val host: String? = config.getPropertyAsString("server.host")
    val hostOrDefault: String? = config.getPropertyAsString("server.host", "localhost")
    val requiredHost: String = config.getRequiredPropertyAsString("server.host")

    // --- Typed access (works when the source stores typed values, e.g. MapPropertySource) ---
    val port: Int? = config.getProperty("server.port", Int::class, 8080)
    val requiredPort: Int = config.getRequiredProperty("server.port", Int::class, 8080)

    // --- Reified extensions (Kotlin only) ---
    val timeout: Long? = config.getProperty<Long>("server.timeout", 30_000L)

    // --- Existence check ---
    val hasFeature: Boolean = config.containsProperty("feature.enabled")

    // --- Bulk access ---
    val allProps: Map<String, Any> = config.getAllProperties()
    val allStrings: Map<String, String> = config.getAllPropertiesAsString() // redacted by default
    val dbProps: Map<String, Any> = config.getSubProperties(setOf("database"), stripPrefix = true)
}
```

**Important:** Property files return string values. If you use `getProperty(key, Int::class)` against a file-backed source, the value is retrieved as a `String` and cast, which may return `null`. Use `getPropertyAsString()` with manual parsing, or use `ConfigBinder` for reliable type conversion.

### Level 2: Typed Object Binding via ConfigBinder

When you have multiple related properties, bind them to a `@Serializable` data class:

```kotlin
@Serializable
data class HttpClientConfig(
    val connectTimeout: Int = 5000,
    val readTimeout: Int = 30000,
    val maxRetries: Int = 3
)

@Inject
class HttpService(configService: AppConfigService) {
    private val binder = configService.toConfigBinder()
    val config: HttpClientConfig = binder.getRequiredConfig("http.client")
}
```

See [Typed Configuration Binding](#typed-configuration-binding) for full details.

### Level 3: Polymorphic Binding

For configuration with multiple variants distinguished by a `type` discriminator (e.g., different KMS backends, storage providers):

```kotlin
// Properties:
// kms.providers.primary.type=memory
// kms.providers.primary.persist.keys.during.generation=true
// kms.providers.backup.type=azure
// kms.providers.backup.vault.url=https://myvault.vault.azure.net

val providers: Map<String, KmsProviderConfig> = resolver.getConfigMapPolymorphic<KmsProviderConfig>(
    prefix = "kms.providers",
    json = kmsJson  // Json instance with polymorphic serializers
)
```

See [Polymorphic Configuration Binding](#polymorphic-configuration-binding) for full details.

### PropertyResolver Interface

All config access methods come from `PropertyResolver`, which `ConfigEnvironment` and `ConfigService` extend:

```kotlin
interface PropertyResolver {
    fun containsProperty(key: String): Boolean

    fun <T : Any> getProperty(key: String, targetType: KClass<T>, defaultValue: T? = null): T?
    fun <T : Any> getRequiredProperty(key: String, targetType: KClass<T>, defaultValue: T? = null): T

    fun getPropertyAsString(key: String, defaultValue: String? = null): String?
    fun getRequiredPropertyAsString(key: String, defaultValue: String? = null): String

    fun getAllProperties(): Map<String, Any>
    fun getAllPropertiesAsString(redact: Boolean = true): Map<String, String>

    fun getSubProperties(prefixes: Set<String>, stripPrefix: Boolean = true): Map<String, Any>
    fun getSubPropertiesAsString(prefixes: Set<String>, stripPrefix: Boolean = true, redact: Boolean = true): Map<String, String>
}
```

Reified Kotlin extensions are available for convenience:

```kotlin
inline fun <reified T : Any> PropertyResolver.getProperty(key: String, defaultValue: T? = null): T?
inline fun <reified T : Any> PropertyResolver.getRequiredProperty(key: String, defaultValue: T? = null): T
```

---

## Typed Configuration Binding

The `ConfigBinder` interface provides type-safe binding of flat properties to data classes using kotlinx.serialization. It converts flat property keys (like `database.host`) into nested JSON structures that can be deserialized into `@Serializable` data classes.

### Creating a ConfigBinder

There are two ways to create a `ConfigBinder`:

```kotlin
// From ConfigService or ConfigEnvironment (recommended — supports interpolation)
val binder = configService.toConfigBinder()

// From any PropertyResolver (no interpolation support)
val binder = resolver.toConfigBinder()
```

The `ConfigEnvironment.toConfigBinder()` extension supports additional options:

```kotlin
fun ConfigEnvironment.toConfigBinder(
    json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    mergeStrategy: JsonMergeStrategy = JsonMergeStrategy.DEEP_MERGE_REPLACE_ARRAYS,
    interpolate: Boolean = true,  // Enable ${...} placeholder resolution
    interpolator: PropertyInterpolator? = null  // Custom interpolator (optional)
): ConfigBinder
```

When `interpolate = true` (default), the binder uses `PropertyResolverFactory` internally to resolve `${...}` placeholders before binding. Set to `false` for faster binding when interpolation is not needed.

### Basic Usage

```kotlin
@Serializable
data class DatabaseConfig(
    val host: String,
    val port: Int,
    val name: String,
    val ssl: Boolean = false
)

// Get optional config (returns null if prefix has no properties)
val config: DatabaseConfig? = binder.getConfig<DatabaseConfig>("database")

// Get required (throws IllegalStateException if missing)
val config: DatabaseConfig = binder.getRequiredConfig<DatabaseConfig>("database")

// Get as IdkResult (recommended for error handling)
val result: IdkResult<DatabaseConfig, IdkError> = binder.getConfigResult<DatabaseConfig>("database")
when {
    result.isOk -> useConfig(result.value)
    result.isErr -> handleError(result.error)
}
```

### How Property-to-Field Mapping Works

Properties are converted from flat dot-notation to nested JSON, then deserialized via kotlinx.serialization.

**Important:** Each dot in the property key (after prefix stripping) creates a nested JSON object. The binder uses the `@Serializable` descriptor to greedily reconstruct camelCase field names from normalized segments. For example, with property `http.connect.timeout` and a descriptor field named `connectTimeout`, the binder matches `connect.timeout` back to `connectTimeout`.

| Property Key | After Prefix Strip | JSON Structure | Data Class |
|--------------|-------------------|----------------|------------|
| `database.host` | `host` | `{ "host": "localhost" }` | `host: String` |
| `database.port` | `port` | `{ "port": 5432 }` | `port: Int` |
| `app.db.host` | `db.host` (prefix=`app`) | `{ "db": { "host": "..." } }` | Needs nested class |

**Nested properties create nested objects:**

```properties
# This creates nested JSON: { "connection": { "timeout": 5000, "retries": 3 } }
http.connection.timeout=5000
http.connection.retries=3
```

```kotlin
@Serializable
data class ConnectionConfig(val timeout: Int, val retries: Int)

@Serializable
data class HttpConfig(
    val connection: ConnectionConfig  // Nested object, NOT connectionTimeout!
)

val config = binder.getConfig<HttpConfig>("http")
// config.connection.timeout = 5000
```

**If you want flat field names (no nesting), use single-segment keys after the prefix:**

```properties
# Flat structure: { "timeout": 5000, "retries": 3 }
http.timeout=5000
http.retries=3
```

```kotlin
@Serializable
data class HttpConfig(val timeout: Int, val retries: Int)
```

### Config Key Casing Contract (Property Sources vs REST)

For config ingestion from property sources (property files, settings stores, programmatic maps), key style should not block resolution for alias-enabled polymorphic configs (for example KMS providers and keystores).

For a field such as `persistKeysDuringGeneration`, the following styles are accepted during property ingestion:

- `persistKeysDuringGeneration`
- `persist.keys.during.generation`
- `persist_keys_during_generation`
- `persist-keys-during-generation`
- `persistkeysduringgeneration`

Source precedence still applies first:

- Higher-priority sources (lower `Order` value) win over lower-priority sources.
- If different key styles map to the same field, the higher-priority source value is retained.

REST/API payload contracts are unchanged:

- JSON field names still follow serializer definitions (typically camelCase / `@SerialName`).
- Relaxed key-style handling is for property-source ingestion only, not for external REST contract changes.

### Type Conversion

ConfigBinder automatically converts property values to target types during JSON deserialization:

| Property Value | Target Type | Result |
|----------------|-------------|--------|
| `"8080"` | `Int` | `8080` |
| `"true"` | `Boolean` | `true` |
| `"3.14"` | `Double` | `3.14` |
| `12345` | `String` | `"12345"` |
| `"30000"` | `Long` | `30000L` |

### Default Values

Data class default values are used when properties are missing:

```kotlin
@Serializable
data class ServerConfig(
    val host: String = "localhost",  // Default if not specified
    val port: Int = 8080,            // Default if not specified
    val timeout: Long = 30000        // Default if not specified
)

// Only host specified in properties
// server.host=api.example.com

val config = binder.getConfig<ServerConfig>("server")
// Result: ServerConfig(host="api.example.com", port=8080, timeout=30000)
```

### Nested Configuration

ConfigBinder supports deeply nested structures:

```kotlin
@Serializable
data class DatabaseConfig(val host: String, val port: Int, val name: String)

@Serializable
data class ServerConfig(val host: String, val port: Int)

@Serializable
data class AppConfig(
    val database: DatabaseConfig,
    val server: ServerConfig
)
```

```properties
# application.properties
app.database.host=db.example.com
app.database.port=5432
app.database.name=mydb
app.server.host=localhost
app.server.port=8080
```

```kotlin
val config = binder.getConfig<AppConfig>("app")
// config.database.host = "db.example.com"
// config.server.port = 8080
```

### List Configuration

```properties
# Indexed list configuration
endpoints.0.url=https://primary.example.com
endpoints.0.weight=100
endpoints.1.url=https://secondary.example.com
endpoints.1.weight=50
```

```kotlin
@Serializable
data class EndpointConfig(val url: String, val weight: Int)

val endpoints: List<EndpointConfig> = binder.getConfigList<EndpointConfig>("endpoints")
// Returns: [EndpointConfig("https://primary...", 100), EndpointConfig("https://secondary...", 50)]

// With diagnostics (strict mode returns error if any entry fails)
val result: IdkResult<List<EndpointConfig>, IdkError> =
    binder.getConfigListResult<EndpointConfig>("endpoints", strict = true)
```

### Map Configuration

```properties
# Keyed map configuration
features.analytics.enabled=true
features.analytics.sampling=0.1
features.notifications.enabled=false
```

```kotlin
@Serializable
data class FeatureConfig(val enabled: Boolean, val sampling: Double = 1.0)

val features: Map<String, FeatureConfig> = binder.getConfigMap<FeatureConfig>("features")
// Returns: mapOf("analytics" to FeatureConfig(true, 0.1), "notifications" to FeatureConfig(false, 1.0))

// With diagnostics
val result: IdkResult<Map<String, FeatureConfig>, IdkError> =
    binder.getConfigMapResult<FeatureConfig>("features", strict = true)
```

### Error Handling

ConfigBinder provides three approaches for handling missing or invalid configuration:

```kotlin
// 1. Optional (returns null if missing or invalid)
val config: DatabaseConfig? = binder.getConfig("database")

// 2. Required (throws IllegalStateException if missing)
try {
    val config = binder.getRequiredConfig<DatabaseConfig>("database")
} catch (e: IllegalStateException) {
    // "Required config not found for prefix 'database': ..."
}

// 3. Result type (recommended - explicit error handling)
val result = binder.getConfigResult<DatabaseConfig>("database")
when {
    result.isOk -> useConfig(result.value)
    result.isErr -> {
        when (result.error.code) {
            "NOT_FOUND_ERROR" -> // Prefix has no properties
            "CONFIG_BIND_ERROR" -> // Deserialization failed (type mismatch, missing required field)
            else -> // Other error
        }
    }
}
```

**Common error scenarios:**

| Error | Cause | Solution |
|-------|-------|----------|
| `NOT_FOUND_ERROR` | No properties with given prefix | Check property prefix, verify properties file is loaded |
| `CONFIG_BIND_ERROR` | Type conversion or deserialization failed | Verify property values match expected types |
| `CONFIG_BIND_ERROR` | Missing required field | Add property or provide default value in data class |

### Merge Strategies

When using hierarchical configuration, you can control how parent and child values are merged:

```kotlin
enum class JsonMergeStrategy {
    REPLACE,                    // Child completely replaces parent
    DEEP_MERGE_REPLACE_ARRAYS,  // Deep merge objects, replace arrays (default)
    DEEP_MERGE_CONCAT_ARRAYS    // Deep merge objects, concatenate arrays
}

val binder = configService.toConfigBinder(
    mergeStrategy = JsonMergeStrategy.DEEP_MERGE_CONCAT_ARRAYS
)
```

### ConfigBinder Interface

```kotlin
interface ConfigBinder {
    fun <T> getConfig(prefix: String, serializer: KSerializer<T>): T?
    fun <T> getRequiredConfig(prefix: String, serializer: KSerializer<T>): T
    fun <T> getConfigResult(prefix: String, serializer: KSerializer<T>): IdkResult<T, IdkError>

    fun <T> getConfigList(prefix: String, serializer: KSerializer<T>): List<T>
    fun <T> getConfigListResult(prefix: String, serializer: KSerializer<T>, strict: Boolean = true): IdkResult<List<T>, IdkError>

    fun <T> getConfigMap(prefix: String, serializer: KSerializer<T>): Map<String, T>
    fun <T> getConfigMapResult(prefix: String, serializer: KSerializer<T>, strict: Boolean = true): IdkResult<Map<String, T>, IdkError>
}

// Reified extensions for cleaner Kotlin usage
inline fun <reified T> ConfigBinder.getConfig(prefix: String): T?
inline fun <reified T> ConfigBinder.getRequiredConfig(prefix: String): T
inline fun <reified T> ConfigBinder.getConfigResult(prefix: String): IdkResult<T, IdkError>
inline fun <reified T> ConfigBinder.getConfigList(prefix: String): List<T>
inline fun <reified T> ConfigBinder.getConfigListResult(prefix: String, strict: Boolean = true): IdkResult<List<T>, IdkError>
inline fun <reified T> ConfigBinder.getConfigMap(prefix: String): Map<String, T>
inline fun <reified T> ConfigBinder.getConfigMapResult(prefix: String, strict: Boolean = true): IdkResult<Map<String, T>, IdkError>
```

### HierarchicalConfigBinder

For explicit scope-aware binding:

```kotlin
val binder = HierarchicalConfigBinder(
    environment = configEnvironment,
    mergeStrategy = JsonMergeStrategy.DEEP_MERGE_REPLACE_ARRAYS
)

// Access scope information
val level = binder.getLevel()        // ConfigLevel.TENANT
val profile = binder.getActiveProfile()  // "production"
```

---

## Polymorphic Configuration Binding

The `PolymorphicConfigBinder` supports configuration scenarios where the concrete type is determined at runtime by a discriminator field (e.g., `type`). This is essential for plugin-style configuration such as KMS providers, storage backends, or authentication strategies.

### How It Works

Polymorphic binding uses a `type` suffix to detect entries and `kotlinx.serialization` polymorphic deserialization to dispatch to the correct concrete class:

```properties
# kms.providers.primary.type determines which @SerialName matches
kms.providers.primary.type=memory
kms.providers.primary.persist.keys.during.generation=true

kms.providers.backup.type=azure
kms.providers.backup.vault.url=https://myvault.vault.azure.net
```

The entry detection strategy (`TypeSuffixEntryDetection` by default) scans for keys ending in `.type` and extracts the entry ID from the prefix before `.type`.

### Using PolymorphicConfigBinder

**Via PropertyResolver extension (simplest):**

```kotlin
val providers: Map<String, KmsProviderConfig> = resolver.getConfigMapPolymorphic<KmsProviderConfig>(
    prefix = "kms.providers",
    json = kmsJson
)

// With error diagnostics (strict mode)
val result: IdkResult<Map<String, KmsProviderConfig>, IdkError> =
    resolver.getConfigMapPolymorphicResult<KmsProviderConfig>(
        prefix = "kms.providers",
        json = kmsJson,
        strict = true
    )
```

**Via explicit binder (more control):**

```kotlin
val binder = DefaultPolymorphicConfigBinder(
    prefix = "kms.providers",
    baseClass = KmsProviderConfig::class,
    json = kmsJson,
    entryDetection = TypeSuffixEntryDetection(),        // default: detect by .type suffix
    idFieldName = "id",                                  // auto-populate id from entry key
    nestedPrefixAliases = mapOf("keystore" to "keyStore"), // map normalized prefix to JSON field
    propertyNameAliases = mapOf(                          // map property keys to JSON field names
        "persist.keys.during.generation" to "persistKeysDuringGeneration"
    )
)

// Get all entries
val all: Map<String, KmsProviderConfig> = binder.getEntryConfigsAsMap(resolver)

// Get specific entry
val primary: KmsProviderConfig? = binder.getEntryConfig(resolver, "primary")

// Get entry IDs only
val ids: Set<String> = binder.getEntryIds(resolver)
```

### Defining a Polymorphic Config Hierarchy

```kotlin
@Serializable
sealed class StorageConfig {
    abstract val id: String
}

@Serializable
@SerialName("local")
data class LocalStorageConfig(
    override val id: String,
    val path: String,
    val maxSizeMb: Int = 1024
) : StorageConfig()

@Serializable
@SerialName("s3")
data class S3StorageConfig(
    override val id: String,
    val bucket: String,
    val region: String,
    val endpoint: String? = null
) : StorageConfig()

// Create a Json instance with polymorphic support
val storageJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    serializersModule = SerializersModule {
        polymorphic(StorageConfig::class) {
            subclass(LocalStorageConfig::class, LocalStorageConfig.serializer())
            subclass(S3StorageConfig::class, S3StorageConfig.serializer())
        }
    }
}
```

```properties
# application.properties
storage.backends.documents.type=local
storage.backends.documents.path=/var/data/docs
storage.backends.documents.max.size.mb=2048

storage.backends.archives.type=s3
storage.backends.archives.bucket=my-archive-bucket
storage.backends.archives.region=eu-west-1
```

```kotlin
val backends: Map<String, StorageConfig> = resolver.getConfigMapPolymorphic<StorageConfig>(
    prefix = "storage.backends",
    json = storageJson
)
// backends["documents"] is LocalStorageConfig(id="documents", path="/var/data/docs", maxSizeMb=2048)
// backends["archives"] is S3StorageConfig(id="archives", bucket="my-archive-bucket", region="eu-west-1")
```

### Entry Detection Strategy

The default `TypeSuffixEntryDetection` looks for keys ending in `.type`:

```kotlin
class TypeSuffixEntryDetection(
    private val discriminatorSuffix: String = ".type",
    private val excludedSuffixes: Set<String> = emptySet()
) : EntryDetectionStrategy
```

Implement `EntryDetectionStrategy` for custom detection logic:

```kotlin
fun interface EntryDetectionStrategy {
    fun detectEntryIds(propertyKeys: Set<String>): Set<String>
}
```

### PolymorphicConfigBinder Interface

```kotlin
interface PolymorphicConfigBinder<T : Any> {
    fun getEntryIds(resolver: PropertyResolver): Set<String>
    fun getEntryConfig(resolver: PropertyResolver, entryId: String): T?
    fun getEntryConfigs(resolver: PropertyResolver): List<T>
    fun getEntryConfigsAsMap(resolver: PropertyResolver): Map<String, T>
}

// DefaultPolymorphicConfigBinder also provides Result-returning methods:
// fun getEntryConfigResult(resolver: PropertyResolver, entryId: String): IdkResult<T, IdkError>
// fun getEntryConfigsResult(resolver: PropertyResolver, strict: Boolean = true): IdkResult<List<T>, IdkError>
// fun getEntryConfigsAsMapResult(resolver: PropertyResolver, strict: Boolean = true): IdkResult<Map<String, T>, IdkError>
```

---

## Creating Configurable Components

This section covers the patterns external developers should use when building new components that need configuration.

### Pattern 1: Inject Config at Construction Time

The simplest pattern. Define a `@Serializable` config class and bind it during injection:

```kotlin
// 1. Define the config class
@Serializable
data class MyServiceConfig(
    val endpoint: String,
    val timeout: Long = 30000,
    val retries: Int = 3,
    val apiKey: String? = null  // Optional secret, can use ${secret:env:MY_API_KEY}
)

// 2. Use it in your service
@Inject
@SingleIn(AppScope::class)
class MyService(configService: AppConfigService) {
    private val config: MyServiceConfig = configService.toConfigBinder()
        .getRequiredConfig("my.service")

    suspend fun call(): String {
        // config.endpoint, config.timeout, etc. are available
    }
}
```

```properties
# application.properties
my.service.endpoint=https://api.example.com
my.service.timeout=10000
my.service.api.key=${secret:env:MY_SERVICE_API_KEY}
```

### Pattern 2: Provide Config via DI Module

When multiple services need the same config, or when you want to control construction more precisely:

```kotlin
@Serializable
data class DatabaseConfig(
    val host: String,
    val port: Int = 5432,
    val name: String,
    val username: String,
    val password: String  // Will use ${secret:env:DB_PASSWORD}
)

@ContributesTo(AppScope::class)
interface DatabaseConfigModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabaseConfig(configService: AppConfigService): DatabaseConfig {
        return configService.toConfigBinder().getRequiredConfig("database")
    }
}

// Now any service can inject DatabaseConfig directly
@Inject
class UserRepository(private val dbConfig: DatabaseConfig) {
    // ...
}
```

### Pattern 3: Config with Validation

Use `getConfigResult` for validated configuration:

```kotlin
@Serializable
data class OAuthConfig(
    val clientId: String,
    val clientSecret: String,
    val tokenEndpoint: String,
    val scopes: List<String> = listOf("openid")
)

@Inject
@SingleIn(AppScope::class)
class OAuthService(configService: AppConfigService) {
    private val config: OAuthConfig

    init {
        val result = configService.toConfigBinder()
            .getConfigResult<OAuthConfig>("oauth")

        config = when {
            result.isOk -> result.value
            result.isErr -> throw IllegalStateException(
                "OAuth configuration invalid: ${result.error.message.defaultMessage}"
            )
            else -> throw IllegalStateException("Unexpected error")
        }
    }
}
```

### Pattern 4: Polymorphic Plugin Configuration

When your component supports multiple backends or strategies:

```kotlin
// 1. Define the sealed hierarchy
@Serializable
sealed class CacheConfig {
    abstract val id: String
    abstract val ttlSeconds: Long
}

@Serializable @SerialName("memory")
data class MemoryCacheConfig(
    override val id: String,
    override val ttlSeconds: Long = 300,
    val maxEntries: Int = 1000
) : CacheConfig()

@Serializable @SerialName("redis")
data class RedisCacheConfig(
    override val id: String,
    override val ttlSeconds: Long = 600,
    val host: String,
    val port: Int = 6379
) : CacheConfig()

// 2. Create Json with polymorphic module
val cacheJson = Json {
    ignoreUnknownKeys = true; isLenient = true
    serializersModule = SerializersModule {
        polymorphic(CacheConfig::class) {
            subclass(MemoryCacheConfig::class, MemoryCacheConfig.serializer())
            subclass(RedisCacheConfig::class, RedisCacheConfig.serializer())
        }
    }
}

// 3. Bind in your service
@Inject
@SingleIn(AppScope::class)
class CacheManager(configService: AppConfigService) {
    private val caches: Map<String, CacheConfig> = configService.toConfigBinder()
        .let { binder ->
            // Use PropertyResolver from the config service for polymorphic binding
            val resolver = PropertySourcesPropertyResolver(
                configService.getPropertySources(includeParents = true)
            )
            resolver.getConfigMapPolymorphic<CacheConfig>(
                prefix = "cache.stores",
                json = cacheJson
            )
        }
}
```

```properties
# application.properties
cache.stores.sessions.type=redis
cache.stores.sessions.host=redis.example.com
cache.stores.sessions.port=6379
cache.stores.sessions.ttl.seconds=1800

cache.stores.temp.type=memory
cache.stores.temp.max.entries=500
cache.stores.temp.ttl.seconds=60
```

### Pattern 5: Configuration with Secret Support

Properties that reference secrets are resolved transparently when interpolation is enabled (the default):

```properties
# Secrets via environment variables
database.password=${secret:env:DB_PASSWORD}
jwt.signing.key=${secret:env:JWT_SECRET}

# Secrets via registered providers (e.g., HashiCorp Vault)
api.key=${secret:vault:secret/data/myapp:api-key}

# Mixed interpolation and secrets
database.url=jdbc:postgresql://${database.host}:${database.port}/${database.name}?password=${secret:env:DB_PASSWORD}
```

In code, you access the resolved value as usual:

```kotlin
@Inject
class SecureService(configService: AppConfigService) {
    // The ${secret:env:DB_PASSWORD} is resolved to the actual value
    private val config: DatabaseConfig = configService.toConfigBinder()
        .getRequiredConfig("database")
    // config.password contains the actual secret value
}
```

---

## Configuration Hierarchy

Configuration follows a three-level hierarchy with inheritance:

```
AppConfigService (ConfigLevel.APP, level=10)
  -> TenantConfigService (ConfigLevel.TENANT, level=20)
      -> PrincipalConfigService (ConfigLevel.PRINCIPAL, level=30)
```

### Service Interfaces

```kotlin
interface AppConfigService : ConfigService {
    override val level: ConfigLevel get() = ConfigLevel.APP
    override val parent: ConfigService? get() = null
}

interface TenantConfigService : ConfigService {
    override val level: ConfigLevel get() = ConfigLevel.TENANT
    override val parent: AppConfigService
}

interface PrincipalConfigService : ConfigService {
    override val level: ConfigLevel get() = ConfigLevel.PRINCIPAL
    override val parent: TenantConfigService
}
```

### Inheritance Behavior

- **Child services inherit from parents** - A `TenantConfigService` automatically has access to all `AppConfigService` properties
- **Children can override parent values** - Tenant-specific settings override app-level defaults
- **Resolution walks up the hierarchy** - If a property isn't found at the current level, parents are checked

### Configuration Levels

| Level | Scope | Use Case |
|-------|-------|----------|
| `APP` | Application-wide | Default settings, infrastructure config |
| `TENANT` | Tenant-specific | Organization customization, tenant features |
| `PRINCIPAL` | User-specific | User preferences, personalization |

---

## Property Sources

Property sources provide configuration values from different origins. They are ordered by priority (lower order value = higher priority).

### Built-in Property Sources

| Source | Order | Scope | Availability | Description |
|--------|-------|-------|--------------|-------------|
| `ProtectedEnvPropertySource` | HIGHEST (10) | APP | Core | Environment variables with FINAL_/PROTECTED_ prefix support (default) |
| `DefaultAppMapPropertySource` | MEDIUM (50) | APP | Core | Programmatic defaults (app scope) |
| `DefaultTenantMapPropertySource` | MEDIUM (50) | TENANT | Core | Programmatic defaults (tenant scope) |
| `DefaultPrincipalMapPropertySource` | MEDIUM (50) | PRINCIPAL | Core | Programmatic defaults (principal scope) |
| `PropertiesFileAppPropertySource` | LOW (70) | APP | Default module | `application.properties` files |
| `PropertiesFileTenantPropertySource` | LOW (70) | TENANT | Default module | `tenant.properties` files |
| `PropertiesFilePrincipalPropertySource` | LOW (70) | PRINCIPAL | Default module | `principal.properties` files |
| `MultiplatformSettings*PropertySource` | LOW (70) | APP/TENANT/PRINCIPAL | Settings module | Key/value settings storage |

Notes:
- Property files return string values; use `ConfigBinder` for typed binding or store typed values in programmatic sources.
- Database/cloud providers are available in EDK modules and register through `PropertySourceContribution`.
- `ProtectedEnvPropertySource` is the default environment source everywhere, providing automatic support for `FINAL_` and `PROTECTED_` prefixes.

### Order Constants

```kotlin
enum class Order(val orderValue: Int) {
    HIGHEST(10),  // Environment variables, protected sources
    HIGH(30),     // Reserved for CLI/args sources (not provided by IDK core)
    MEDIUM(50),   // Programmatic defaults
    LOW(70),      // Property files, settings storage
    LOWEST(90)    // Fallbacks
}
```

Lower `orderValue` means higher priority.

### Creating Custom Property Sources

```kotlin
class MyCustomPropertySource(
    private val dataSource: DataSource
) : AbstractPropertySource<DataSource>(
    name = "my-custom-source",
    source = dataSource,
    order = Order.MEDIUM.orderValue
) {
    override fun hasProperty(name: String): Boolean {
        val key = keyNormalizer.normalize(name)
        return dataSource.containsKey(key)
    }

    override fun <T : Any> getProperty(name: String, targetType: KClass<T>): T? {
        val key = keyNormalizer.normalize(name)
        return dataSource.getValue(key) as? T
    }

    override fun getPropertyAsString(name: String): String? {
        val key = keyNormalizer.normalize(name)
        return dataSource.getValue(key)?.toString()
    }

    override fun getAllPropertyNames(): Set<String> {
        return dataSource.getAllKeys()
    }
}
```

### PropertySource Interface

```kotlin
interface PropertySource<out T> : HasOrder, Comparable<PropertySource<*>> {
    val isPlatformSupported: Boolean

    fun getName(): String
    fun getSource(): T
    fun getOrder(): Int

    fun hasProperty(name: String): Boolean
    fun <T : Any> getProperty(name: String, targetType: KClass<T>): T?
    fun getPropertyAsString(name: String): String?
    fun getAllPropertyNames(): Set<String>
    fun removeProperty(name: String)

    override fun compareTo(other: PropertySource<*>): Int = getOrder().compareTo(other.getOrder())
}
```

---

## Property Resolution

### Resolution Algorithm

When resolving a property, the system:

1. **Normalize the key** - Convert to canonical format (`httpTimeout` -> `http.timeout`)
2. **Check current scope's property sources** - In priority order (lowest order value first)
3. **Walk up the hierarchy** - If not found, check parent services
4. **Apply interpolation (optional)** - Resolve `${...}` placeholders when the pipeline/interpolator is enabled
5. **Type conversion** - Only performed by the property source itself or by `ConfigBinder` (the core `PropertyResolver` does not coerce strings)

### Resolution Example

```kotlin
// Given these property sources (in order):
// 1. ProtectedEnvPropertySource (order=10): DATABASE_URL=env-value
// 2. MapPropertySource (order=50): database.url=map-value
// 3. FilePropertySource (order=70): database.url=file-value

val url = config.getPropertyAsString("database.url")
// Returns: "env-value" (ProtectedEnvPropertySource has highest priority)
```

### DefaultConfigResolutionPipeline

For advanced use cases requiring async resolution with metadata, use `DefaultConfigResolutionPipeline`:

```kotlin
val pipeline = DefaultConfigResolutionPipeline(
    propertySources = propertySources,
    interpolator = DefaultPropertyInterpolator(secretResolver),
    keyNormalizer = PropertyKeyNormalizerImpl(),
    resolverLevel = ConfigLevel.APP,
    snapshotCache = InMemorySyncSnapshotCache()
)

// Resolve with full metadata
val result = pipeline.resolve<String>("api.url", ResolutionContext.app())
if (result.isOk) {
    val value = result.value.value
    val metadata = result.value.metadata
    println("Value: $value, Source: ${metadata.source}, Scope: ${metadata.scope}")
}

// Control resolution options
val context = ResolutionContext(
    level = ConfigLevel.TENANT,
    tenantId = "acme-corp",
    options = ResolutionOptions(
        useCache = true,
        interpolate = true,
        resolveSecrets = true,
        maxInterpolationDepth = 10
    )
)
```

The pipeline uses `ProtectedPropertySourcesResolver` internally to enforce FINAL/PROTECTED property restrictions during interpolation.

---

## Property File Support

When the default module (`core/api/default`) is on the classpath, IDK automatically loads `.properties` files from the config directory (`./config`).

Protection prefixes (`final.`, `protected.`, `final.protected.`) are supported in these property files by default.

### File Locations

The config location can be configured via environment variables (checked in order):
1. `SPHEREON_CONFIG_LOCATION` - Primary config directory path
2. `SPHEREON_CONFIG_DIR` - Alternative config directory path
3. Default: `./config`

**Application-level files** (`PropertiesFileAppPropertySource`):
```
./config/application.properties           # Base app config
./config/application-{profile}.properties # Profile-specific overrides
```

**Tenant-level files** (`PropertiesFileTenantPropertySource`):
```
./config/tenant/{tenantId}/tenant.properties           # Tenant config
./config/tenant/{tenantId}/tenant-{profile}.properties # Tenant+profile
```

**Principal-level files** (`PropertiesFilePrincipalPropertySource`):
```
./config/tenant/{tenantId}/principal/{principalId}/principal.properties
./config/tenant/{tenantId}/principal/{principalId}/principal-{profile}.properties
```

### Directory Structure Example

```
./config/
|-- application.properties               # App defaults
|-- application-production.properties    # Production overrides
|-- application-staging.properties       # Staging overrides
`-- tenant/
    |-- acme-corp/
    |   |-- tenant.properties            # Acme Corp config
    |   |-- tenant-production.properties
    |   `-- principal/
    |       `-- alice/
    |           |-- principal.properties
    |           `-- principal-production.properties
    `-- globex/
        `-- tenant.properties            # Globex config
```

### Loading Behavior

- **Missing files are silent** - No errors if property files don't exist
- **Profile files override base files** - `application-production.properties` overrides `application.properties`
- **Auto-registration** - Property sources register automatically with DI
- **Values are strings** - Property file sources return strings; use `ConfigBinder` for typed binding

---

## Key Normalization

Property keys are automatically normalized for consistent access regardless of naming convention.

Note: `ProtectedEnvPropertySource` lowercases environment variable names before normalization, so `HTTP_TIMEOUT` becomes `http.timeout`.

### Normalization Rules

| Input | Normalized Output |
|-------|-------------------|
| `httpTimeout` | `http.timeout` |
| `HTTP_TIMEOUT` (env var) | `http.timeout` |
| `http-timeout` | `http.timeout` |
| `http timeout` | `http.timeout` |
| `http.timeout` | `http.timeout` |
| `HttpTimeout` | `http.timeout` |

Note: all-lowercase flat keys without delimiters (for example `persistkeysduringgeneration`) cannot be split by normalization alone. For polymorphic config binders that define field aliases, these keys are still supported during property ingestion.

### Algorithm

1. Replace underscores, hyphens, and spaces with dots
2. Insert dots before uppercase letters (camelCase split)
3. Lowercase uppercase letters as they are processed
4. Collapse multiple consecutive dots

### Usage

```kotlin
// All of these access the same property:
resolver.getPropertyAsString("httpTimeout")
resolver.getPropertyAsString("http.timeout")
resolver.getPropertyAsString("http-timeout")
```

### PropertyKeyNormalizer

```kotlin
interface PropertyKeyNormalizer {
    fun normalize(key: String): String
}
```

Default implementation: `PropertyKeyNormalizerImpl`.

The inverse operation is available via `PropertyKeyDenormalizer` / `CamelCaseKeyDenormalizerImpl`, which converts `http.timeout` back to `httpTimeout`. This is used internally by `ConfigBinder` when reconstructing JSON field names.

---

## Profile-Based Configuration

Profiles allow environment-specific configuration without code changes.

### Setting the Active Profile

**Via DI (recommended):**
```kotlin
@ContributesTo(AppScope::class)
interface AppConfigModule {
    @Provides
    @Named("profile")
    fun provideProfile(): String = "production"

    @Provides
    @Named("appId")
    fun provideAppId(): String = "my-app"
}
```

**Derive from environment (optional):**
```kotlin
val resolver = DefaultProfileResolver() // reads SPHEREON_PROFILES_ACTIVE (comma-separated)
val profile = resolver.getActiveProfiles().firstOrNull() ?: resolver.defaultProfile
```

Note: Property file loading uses a single active profile string (no profile chains).

### Profile Resolution

When profile is "production":

1. First loads `application.properties`
2. Then loads `application-production.properties` (overrides base)
3. Properties from profile file override base file

---

## Property Interpolation

Properties can reference other properties using `${...}` syntax. Interpolation is only active when the pipeline/interpolator is enabled (the default module wires this automatically; otherwise use `PropertyResolverFactory.withInterpolation`).

The interpolator supports multiple prefix types with a well-defined resolution order.

### Supported Patterns

| Pattern | Type | Description |
|---------|------|-------------|
| `${key}` | SIMPLE | Property lookup |
| `${key:default}` | SIMPLE | Property lookup with default value |
| `${env:VAR}` | ENV | Environment variable lookup |
| `${env:VAR:default}` | ENV | Environment variable with default |
| `${app:key}` | SCOPE | App-scope property lookup |
| `${tenant:key}` | SCOPE | Tenant-level property lookup |
| `${principal:key}` | SCOPE | Principal-level property lookup |
| `${secret:provider:path}` | SECRET | Secret from provider |
| `${secret:provider:path:key}` | SECRET | Specific key within secret |
| `${db.${env}}` | Nested | Recursive interpolation (max depth 10) |

### Pattern Resolution Order (Security)

**IMPORTANT**: Prefixes are checked FIRST, before interpreting colons as default value separators. This prevents property injection attacks.

The parsing order is:
1. **`secret:`** - Checked first: `${secret:provider:path}`
2. **`env:`** - Checked second: `${env:VAR_NAME}`
3. **`app:`/`tenant:`/`principal:`** - Checked third: `${scope:key}`
4. **Simple** - Default: colon treated as default value separator

**Example of secure resolution:**
```properties
# Even if someone sets env=malicious in properties:
env=malicious

# This is SAFE - ${env:HOME} is recognized as ENV type, not SIMPLE with default
config.value=${env:HOME}
# -> Resolves to environment variable HOME, NOT property "env" with default "HOME"

# This looks up the PROPERTY named "env"
other.value=${env}
# -> Resolves to "malicious" (property lookup)
```

### Basic Interpolation

```properties
# application.properties
base.url=https://api.example.com
users.endpoint=${base.url}/v1/users
health.endpoint=${base.url}/health
```

```kotlin
val endpoint = config.getPropertyAsString("users.endpoint")
// Returns: "https://api.example.com/v1/users"
```

### Default Values

```properties
# Simple property with default
database.host=${db.host:localhost}
database.port=${db.port:5432}

# Environment variable with default
log.level=${env:LOG_LEVEL:INFO}

# Default can contain colons
base.url=${env:API_URL:http://localhost:8080}
```

### Environment Variable Lookup

```properties
# Explicit environment variable lookup
database.password=${env:DB_PASSWORD}

# With fallback default
database.host=${env:DB_HOST:localhost}

# WARNING: ${env} (without colon) is a PROPERTY lookup, not env var!
```

### Scope-Based Lookup

Scope-prefixed lookups resolve the key against the specified scope's property sources:

```properties
# App-level (global) config
database.url=${app:database.url}

# Tenant-specific override
tenant.api.key=${tenant:api.key}
```

When using scope prefixes, the key is resolved at that scope:
- `${app:database.url}` -> looks up `database.url` from APP scope
- `${tenant:api.key}` -> looks up `api.key` from TENANT scope

In most cases you should rely on the normal hierarchy (tenant/principal inherit app values) and avoid scope prefixes.

### Nested/Recursive Interpolation

```properties
current.env=prod
database.url=${db.${current.env}.url}

db.dev.url=jdbc:postgresql://localhost/mydb
db.prod.url=jdbc:postgresql://prod-db.example.com/mydb
# Result: ${database.url} -> "jdbc:postgresql://prod-db.example.com/mydb"
```

### Edge Cases and Gotchas

| Input | Behavior |
|-------|----------|
| `${env}` | Looks up **property** named "env" (NOT environment variable!) |
| `${env:}` | Looks up property "env" with empty string default |
| `${env:VAR}` | Looks up **environment variable** VAR |
| `${env:VAR:default}` | Looks up env var VAR, falls back to "default" |
| `${app}` | Looks up property named "app" |
| `${app:key}` | Looks up `key` from APP scope (scope-aware lookup) |
| `${secret:vault}` | Invalid - requires path (error) |
| `${unclosed` | Returned as-is (no matching brace) |
| `${DB_HOST}` | Looks up property `db.host` (key normalized!) |

**Key Normalization in SIMPLE lookups:** Property keys are always normalized before lookup:
- Uppercase -> lowercase: `HOST` -> `host`
- Underscores -> dots: `DB_HOST` -> `db.host`
- CamelCase -> dot-separated: `myAppConfig` -> `my.app.config`

This means `${DB_HOST}` is NOT the same as `${env:DB_HOST}`:
- `${DB_HOST}` -> property lookup for `db.host`
- `${env:DB_HOST}` -> environment variable lookup for `DB_HOST` (no normalization)

### Interpolation Limits

- **Maximum depth: 10 levels** - Prevents infinite recursion
- **Circular reference detection** - Returns error with clear message
- **Missing property handling** - Uses default value or returns error

### PropertyInterpolator Interface

```kotlin
interface PropertyInterpolator {
    suspend fun interpolate(value: String, resolver: PropertyResolver): IdkResult<String, IdkError>

    suspend fun interpolate(
        value: String,
        resolver: PropertyResolver,
        requestingScope: ConfigLevel,
        maxDepth: Int? = null,
        resolveSecrets: Boolean = true
    ): IdkResult<String, IdkError>

    fun containsPlaceholders(value: String): Boolean
    fun isSecretReference(value: String): Boolean
    fun parsePlaceholders(value: String): List<PlaceholderToken>
}
```

### PlaceholderToken and Types

```kotlin
enum class PlaceholderType {
    SIMPLE,     // ${key} or ${key:default}
    ENV,        // ${env:VAR} or ${env:VAR:default}
    SCOPE,      // ${app:key}, ${tenant:key}, ${principal:key}
    SECRET      // ${secret:provider:path} or ${secret:provider:path:key}
}

data class PlaceholderToken(
    val fullMatch: String,
    val type: PlaceholderType,
    val key: String,
    val defaultValue: String?,
    val provider: String?,
    val path: String?
)
```

---

## Secret Management

The configuration system integrates with external secret providers for sensitive values.

### Secret Reference Syntax

Secret references are resolved by the interpolator. By default, secrets are only accessible at APP scope.
Tenant/principal scopes can access secrets if the path starts with `tenant/` or `principal/` (or `tenants/`, `principals/`, `users/`).

```properties
# Environment variable (built-in env provider)
jwt.secret=${secret:env:JWT_SECRET}

# Map provider (register MapSecretProvider)
database.password=${secret:map:local-secrets:db-password}

# Custom providers (if registered)
api.key=${secret:vault:secret/data/myapp:api-key}
```

### SecretProvider Interface

```kotlin
interface SecretProvider {
    val providerId: String      // Unique identifier (e.g., "env", "vault", "azure")
    val isAvailable: Boolean    // Whether provider is operational

    suspend fun getSecret(
        path: String,
        key: String? = null,
        scope: ConfigLevel = ConfigLevel.APP,
        scopeIdentifier: String? = null,
        options: SecretOptions = SecretOptions()
    ): IdkResult<SecretValue, SecretError>

    suspend fun invalidateCache(path: String? = null)
    suspend fun healthCheck(): IdkResult<ProviderHealth, SecretError>
}
```

### SecretResolver Interface

The `SecretResolver` interface is used by the interpolator for resolving `${secret:...}` placeholders:

```kotlin
interface SecretResolver {
    suspend fun resolve(provider: String, path: String, key: String?): IdkResult<String, IdkError>

    suspend fun resolve(
        provider: String,
        path: String,
        key: String?,
        scope: ConfigLevel?,
        scopeIdentifier: String?
    ): IdkResult<String, IdkError>
}
```

### Built-in Secret Providers

| Provider | ID | Description |
|----------|-----|-------------|
| `EnvSecretProvider` | `env` | Environment variables (registered by default) |
| `MapSecretProvider` | `map` | In-memory map (register manually for tests) |

### Implementing a Custom Secret Provider

```kotlin
class VaultSecretProvider(
    private val vaultClient: VaultClient
) : SecretProvider {
    override val providerId = "vault"
    override val isAvailable get() = vaultClient.isConnected

    override suspend fun getSecret(
        path: String,
        key: String?,
        scope: ConfigLevel,
        scopeIdentifier: String?,
        options: SecretOptions
    ): IdkResult<SecretValue, SecretError> {
        return try {
            val secret = vaultClient.read(path)
            val value = if (key != null) secret.data[key] else secret.data.values.first()
            Ok(SecretValue(value = value.toString()))
        } catch (e: Exception) {
            Err(SecretError.general(providerId, path, e.message ?: "unknown", e))
        }
    }

    override suspend fun invalidateCache(path: String?) { /* ... */ }
    override suspend fun healthCheck(): IdkResult<ProviderHealth, SecretError> { /* ... */ }
}

// Register with the registry
val registry = SecretProviderRegistry()
registry.register(VaultSecretProvider(vaultClient))
```

### Secret Resolution Options

```kotlin
data class SecretOptions(
    val timeout: Duration = 5.seconds,
    val cacheOverride: Boolean = false,
    val bypassCache: Boolean = false
)
```

### Secret Redaction

**Automatic redaction is enabled by default** when using string conversion methods. The following methods redact sensitive values by default:

```kotlin
configService.getAllPropertiesAsString()                    // Redacts sensitive keys
configService.getSubPropertiesAsString(setOf("app"))        // Redacts sensitive keys
```

To retrieve raw (unredacted) values, pass `redact = false`:

```kotlin
val rawProps = configService.getAllPropertiesAsString(redact = false)
```

**Note:** `getAllProperties()` always returns raw `Map<String, Any>` (needed for ConfigBinder).

#### DefaultSecretRedactionPolicy

Values are redacted based on:
1. **Metadata flag**: If `ResolutionMetadata.isSecret` is `true` (set when value was resolved via `${secret:...}`)
2. **Key pattern matching**: If the property key matches sensitive patterns

```kotlin
class DefaultSecretRedactionPolicy(
    private val redactedPlaceholder: String = "***REDACTED***",
    private val sensitiveKeyPatterns: List<Regex> = listOf(
        Regex(".*password.*", RegexOption.IGNORE_CASE),
        Regex(".*secret.*", RegexOption.IGNORE_CASE),
        Regex(".*token.*", RegexOption.IGNORE_CASE),
        Regex(".*key.*", RegexOption.IGNORE_CASE),
        Regex(".*credential.*", RegexOption.IGNORE_CASE),
        Regex(".*auth.*", RegexOption.IGNORE_CASE)
    )
) : SecretRedactionPolicy
```

#### Custom Redaction Policy

```kotlin
class StrictRedactionPolicy : SecretRedactionPolicy {
    private val alwaysRedactPatterns = listOf(
        Regex(".*password.*", RegexOption.IGNORE_CASE),
        Regex(".*secret.*", RegexOption.IGNORE_CASE),
        Regex(".*private[._-]?key.*", RegexOption.IGNORE_CASE)
    )

    override fun shouldRedact(key: String, metadata: ResolutionMetadata): Boolean {
        if (metadata.isSecret) return true
        return alwaysRedactPatterns.any { it.matches(key) }
    }

    override fun redact(value: String): String = "****"
}
```

### Secret Path-Based Access Control

Secrets (`${secret:...}`) have automatic path-based protection:

| Path Pattern | Accessible From |
|--------------|-----------------|
| `system/*`, `app/*` | APP scope only |
| `tenant/*`, `tenants/*` | TENANT scope and above |
| `principal/*`, `principals/*`, `user/*`, `users/*` | PRINCIPAL scope and above |
| Other paths | APP scope only (default) |

---

## Property Protection

In multi-tenant SaaS deployments, certain configuration properties need protection from being overridden or accessed at lower scope levels. The property protection system provides two key security controls:

1. **FINAL** - Prevents properties from being overridden at lower scopes (e.g., tenant cannot override app-level database host)
2. **PROTECTED** - Prevents properties from being interpolated from lower scopes (e.g., tenant config cannot reference app-level secrets)

### Protection Prefixes

| Prefix | Environment Variable | Effect |
|--------|---------------------|--------|
| `final.` | `FINAL_` | Cannot be overridden at lower scope levels |
| `protected.` | `PROTECTED_` | Cannot be interpolated from lower scope levels |
| `final.protected.` | `FINAL_PROTECTED_` | Both protections |

### Examples

**Environment variables:**
```bash
FINAL_DB_HOST=prod.db.example.com
PROTECTED_INTERNAL_API_KEY=abc123
FINAL_PROTECTED_DB_PASSWORD=super-secret
```

**Properties files:**
```properties
final.db.host=prod.db.example.com
protected.internal.api.key=abc123
final.protected.db.password=super-secret
app.theme=light  # Regular, no protection
```

### How It Works

When loading `final.protected.db.password=secret`:

1. **Parse prefixes**: Extract `final.`, `protected.` from key
2. **Register canonical key**: `db.password` (prefixes stripped)
3. **Store protection metadata**: `db.password` -> `{FINAL, PROTECTED, definedAt: APP}`
4. **Store value**: `db.password` -> `secret`

### Security Model

**Override attempt (TENANT tries to set `db.password`):**
```
1. Check protection registry for "db.password"
2. Found: FINAL at APP scope
3. TENANT.level (20) > APP.level (10) -> DENY override
4. Error: "Property 'db.password' is marked FINAL at APP scope"
```

**Interpolation attempt (TENANT config has `${app:db.password}`):**
```
1. Check protection registry for "db.password"
2. Found: PROTECTED at APP scope
3. TENANT.level (20) > APP.level (10) -> DENY interpolation
4. Error: "Property 'db.password' is marked PROTECTED at APP scope"
5. Value never accessed!
```

### Key Interfaces

| Interface | Description |
|-----------|-------------|
| `PropertyProtection` | Protection metadata (isFinal, isInterpolationProtected, definedAt) |
| `ProtectionKeyParser` | Parses protection prefixes from keys |
| `ProtectedPropertySource<T>` | Property source with protection tracking |
| `ProtectedPropertyResolver` | Resolver that enforces protection rules |

---

## Extending the Configuration System

### PropertySourceContribution

To add custom property sources that auto-register via DI, implement `PropertySourceContribution`:

```kotlin
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = PropertySourceContribution::class, multibinding = true)
class MyProviderContribution(
    private val provider: MyCloudProvider
) : PropertySourceContribution {

    override val configLevel = ConfigLevel.APP
    override val providerId = "my-cloud-provider"

    override fun isEnabled(resolver: PropertyResolver): Boolean {
        val disabled = resolver.getProperty(
            "config.providers.my-cloud-provider.enabled",
            Boolean::class
        )
        return disabled != false  // Enabled by default
    }

    override fun getPropertySource(): PropertySource<*> = provider

    // For providers needing async initialization
    override val requiresAsyncInit: Boolean = true

    override suspend fun initialize() {
        provider.refreshFromRemote()
    }

    override fun getOrder(): Int = Order.MEDIUM.orderValue
}
```

### Configuration Keys for Providers

```properties
# Disable a provider
config.providers.my-cloud-provider.enabled=false

# Override provider order (only if your contribution reads this key)
config.providers.my-cloud-provider.order=40
```

Note: `PropertySourceBootstrap` does not apply the order key automatically. If you want dynamic ordering, read this value inside `getOrder()` in your contribution.

### PropertySourceBootstrap

The `PropertySourceBootstrap` service handles registration:

```kotlin
interface PropertySourceBootstrap {
    fun registerAppSources()
    suspend fun initializeAsync()
    fun registerTenantSources(tenantConfigService: ConfigService, tenantId: String)
    fun registerPrincipalSources(principalConfigService: ConfigService, tenantId: String, principalId: String)

    val contributions: Set<PropertySourceContribution>
    val registeredAppSourceCount: Int
}
```

### Cloud Configuration Provider Interface

For cloud-backed configuration with refresh and change watching:

```kotlin
interface CloudConfigProvider : PropertySource<Map<String, Any>> {
    val providerId: String
    val isAvailable: Boolean
    val configLevel: ConfigLevel
    val lastRefreshedAt: Instant?

    suspend fun refresh(): IdkResult<RefreshResult, IdkError>
    fun watchForChanges(): Flow<ConfigChangeEvent>
    suspend fun startWatching()
    suspend fun stopWatching()
    suspend fun healthCheck(): IdkResult<CloudProviderHealth, IdkError>
}
```

### EDK / VDX Extensions

The EDK layer adds database-backed and cloud property sources on top of the IDK core. These register automatically via `PropertySourceContribution` when their modules are on the classpath. VDX further adds domain-specific configuration for its API platform services. See EDK and VDX documentation for details on those layers.

---

## Caching

Caching is applied in two places:

1. Snapshot caching for prefix queries (`getSubProperties`) via `CachingPropertySourcesPropertyResolver` and `SyncConfigSnapshotCache`.
2. Optional async cache warmup for external caches via `ConfigCacheWarmup` and `AsyncToSyncCacheAdapter`.

Single-property lookups (`getProperty*`) are not cached by default.

### Snapshot Cache (Default)

- `SyncConfigSnapshotCache` is used by default by config environments.
- `InMemorySyncSnapshotCache` is the default DI binding.
- TTL is per scope via `TtlConfig` (`app`, `tenant`, `principal`).
- Cache keys include scope + tenant/principal IDs + prefix.

### Manual Invalidation

```kotlin
// Sync snapshot cache
syncCache.invalidateByPrefix("kms.providers")
syncCache.clear()

// CachingPropertySourcesPropertyResolver
cachingResolver.invalidate("kms.providers")  // Invalidate by prefix
cachingResolver.invalidate()                  // Clear all cached entries

// ConfigResolutionPipeline invalidation (when snapshotCache is provided)
pipeline.invalidate("kms.providers", context)
```

---

## Dependency Injection Integration

The configuration system integrates with kotlin-inject-anvil for DI.

### Scope-Based Configuration

```kotlin
// App-scoped configuration (singleton)
@Inject
@SingleIn(AppScope::class)
class MyAppService(
    private val configService: AppConfigService
) {
    val appName = configService.getAppName()
    val profile = configService.getActiveProfile()
}

// Tenant-scoped configuration (UserScope)
@Inject
@SingleIn(UserScope::class)
class MyTenantService(
    private val configService: TenantConfigService
) {
    val tenantFeature = configService.getPropertyAsString("feature.enabled")?.toBoolean() ?: false
}
```

### Injecting ConfigBinder

```kotlin
@Inject
@SingleIn(AppScope::class)
class ConfiguredService(
    private val configService: AppConfigService
) {
    private val binder = configService.toConfigBinder()

    val httpConfig: HttpClientConfig = binder.getRequiredConfig("http.client")
    val dbConfig: DatabaseConfig = binder.getRequiredConfig("database")
}
```

---

## Troubleshooting

### Common Issues

#### Property Not Found

```kotlin
// Error: IllegalStateException: Required config not found for prefix 'database'

// Solution 1: Check property file exists and key is correct
// Solution 2: Use getConfig() with null handling instead of getRequiredConfig()
val config = binder.getConfig<DatabaseConfig>("database")
    ?: DatabaseConfig.default()

// Solution 3: Check key normalization
// "database.poolSize" normalizes to "database.pool.size"
```

#### Type Conversion Errors

```kotlin
// Error: SerializationException: Failed to deserialize config

// Ensure your data class uses @Serializable
@Serializable
data class MyConfig(val value: Int)

// Ensure property values match expected types
# Wrong: myconfig.value=not-a-number
# Correct: myconfig.value=42
```

#### Circular Reference Detection

```kotlin
// Error: Circular reference detected: key1 -> key2 -> key1

# application.properties
key1=${key2}
key2=${key1}  # Creates circular reference
```

#### Secret Provider Unavailable

```kotlin
// Error: [PROVIDER_UNAVAILABLE] Secret provider 'vault' is not available

// Check 1: Provider is registered
registry.hasProvider("vault")  // Should return true

// Check 2: Provider is available
registry.get("vault")?.isAvailable  // Should return true
```

### Verifying Configuration

```kotlin
// List all registered property sources
val sources = configService.getPropertySources()
sources.forEach { source ->
    println("Source: ${source.getName()}, Order: ${source.getOrder()}")
}

// List all properties (redacted by default)
val allProps = configService.getAllPropertiesAsString()
allProps.forEach { (key, value) ->
    println("$key = $value")
}

// Check PropertySourceBootstrap registration
val bootstrap: PropertySourceBootstrap = ...
println("Registered ${bootstrap.registeredAppSourceCount} APP sources")
bootstrap.contributions.forEach { contribution ->
    println("Contribution: ${contribution.providerId} (${contribution.configLevel})")
}
```

---

## Best Practices

### 1. Use ConfigBinder for Structured Configuration

```kotlin
// Good - structured, validated configuration
@Serializable
data class HttpClientConfig(
    val connectTimeout: Int = 5000,
    val readTimeout: Int = 30000,
    val maxRetries: Int = 3
)
val config = binder.getRequiredConfig<HttpClientConfig>("http.client")

// Avoid - scattered property access
val connectTimeout = config.getPropertyAsString("http.client.connect.timeout")?.toIntOrNull() ?: 5000
val readTimeout = config.getPropertyAsString("http.client.read.timeout")?.toIntOrNull() ?: 30000
```

### 2. Provide Defaults in Data Classes

```kotlin
// Good - sensible defaults make configuration optional
@Serializable
data class ServerConfig(
    val host: String = "localhost",
    val port: Int = 8080,
    val timeout: Long = 30000
)
```

### 3. Use getRequiredProperty for Mandatory Configuration

```kotlin
// Good - fails fast if missing
val apiKey = config.getRequiredPropertyAsString("api.key")

// Risky - silent null that causes issues later
val apiKey = config.getPropertyAsString("api.key")
```

### 4. Group Related Properties Under a Common Prefix

```properties
# Good - clear grouping
database.host=localhost
database.port=5432
database.name=myapp
database.pool.min=5
database.pool.max=20

# Avoid - scattered naming
dbHost=localhost
db_port=5432
databaseName=myapp
```

### 5. Use Secret References for Sensitive Values

```properties
# Good - secrets managed externally
database.password=${secret:env:DB_PASSWORD}
api.key=${secret:vault:api-key}

# Never - hardcoded secrets
database.password=MyS3cr3tP@ssw0rd
```

### 6. Use Profiles for Environment-Specific Config

```properties
# application.properties
log.level=INFO
feature.debug=false

# application-development.properties
log.level=DEBUG
feature.debug=true
```

### 7. Use Result Types for Error Handling

```kotlin
// Recommended - explicit error handling
val result = binder.getConfigResult<DatabaseConfig>("database")
if (result.isErr) {
    log.error("Config load failed: ${result.error.message.defaultMessage}")
    // Handle gracefully
}
```

### 8. Document Configuration Keys

```kotlin
/**
 * Configuration for the HTTP client.
 *
 * Properties:
 * - `http.connect.timeout` (Int, default: 5000) - Connection timeout in ms
 * - `http.read.timeout` (Int, default: 30000) - Read timeout in ms
 * - `http.max.retries` (Int, default: 3) - Maximum retry attempts
 */
@Serializable
data class HttpClientConfig(
    val connectTimeout: Int = 5000,
    val readTimeout: Int = 30000,
    val maxRetries: Int = 3
)
```

---

## API Reference

### Core Interfaces

| Interface | Description |
|-----------|-------------|
| `ConfigService` | Mutable configuration service with add/remove property sources |
| `AppConfigService` | Application-level configuration (extends ConfigService) |
| `TenantConfigService` | Tenant-level configuration (extends ConfigService) |
| `PrincipalConfigService` | Principal-level configuration (extends ConfigService) |
| `ConfigEnvironment` | Configuration environment with property access |
| `PropertyResolver` | Read-only property access |
| `ScopeAwarePropertyResolver` | PropertyResolver with scope-aware getPropertyAtScope() |
| `PropertySource<T>` | Provider of configuration properties |
| `ScopedPropertySource<T>` | PropertySource with explicit ConfigLevel |
| `ConfigBinder` | Type-safe configuration binding |
| `PolymorphicConfigBinder<T>` | Polymorphic configuration binding with discriminator support |
| `PropertyKeyNormalizer` | Key normalization strategy |
| `PropertyKeyDenormalizer` | Inverse key normalization (dot-separated to camelCase) |
| `PropertyInterpolator` | Value interpolation |
| `SecretProvider` | External secret resolution |
| `SecretResolver` | Interpolator-facing secret resolution |
| `SecretProviderRegistry` | Registry for secret providers |
| `SecretRedactionPolicy` | Policy for masking sensitive values |
| `ConfigResolutionPipeline` | Async resolution with metadata and scope-aware protection enforcement |
| `PropertySourceContribution` | Auto-registration for property sources |
| `PropertySourceBootstrap` | Bootstrap service for property source registration |
| `CloudConfigProvider` | Cloud configuration provider with refresh |
| `EntryDetectionStrategy` | Strategy for detecting polymorphic config entry IDs |

### Configuration Levels

| Level | Value | Description |
|-------|-------|-------------|
| `ConfigLevel.APP` | 10 | Application-wide settings |
| `ConfigLevel.TENANT` | 20 | Tenant-specific settings |
| `ConfigLevel.PRINCIPAL` | 30 | User-specific settings |

### Order Constants

| Constant | Value | Use Case |
|----------|-------|----------|
| `Order.HIGHEST` | 10 | Environment variables |
| `Order.HIGH` | 30 | Reserved for CLI/args sources (custom) |
| `Order.MEDIUM` | 50 | Programmatic defaults |
| `Order.LOW` | 70 | Property files |
| `Order.LOWEST` | 90 | Fallback defaults |

### Key Data Classes

| Class | Description |
|-------|-------------|
| `SecretValue` | Resolved secret with metadata (value, expiry, version) |
| `SecretError` | Secret resolution error (code, message, provider, path) |
| `SecretOptions` | Options for secret resolution (timeout, cache bypass) |
| `ProviderHealth` | Secret provider health status |
| `PlaceholderToken` | Parsed interpolation placeholder |
| `RefreshResult` | Cloud provider refresh result |
| `ConfigChangeEvent` | Configuration change notification |
| `ResolutionMetadata` | Full metadata for a resolved property (source, scope, isSecret, isInterpolated) |
| `PropertyProtection` | Protection metadata (isFinal, isInterpolationProtected, definedAt) |

---

## Related Documentation

- [CORE-CONCEPTS.md](./CORE-CONCEPTS.md) - Overview of core APIs including configuration summary
- [ARCHITECTURE.md](./ARCHITECTURE.md) - Command pattern and extension system
- [EDK Configuration Modules](../../../../lib/conf/docs/OVERVIEW.md) - Database and cloud configuration providers

---

*Copyright 2025 Sphereon International B.V. - Apache License 2.0*
