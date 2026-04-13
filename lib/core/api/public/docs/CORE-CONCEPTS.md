# IDK Core Concepts Documentation

This document covers the non-command core APIs of the IDK (Identity Development Kit) Core API module.

For canonical API choices and transitional paths, see [API-SURFACE.md](./API-SURFACE.md).
For starter extension scaffolds, see [EXTENSIONS.md](./EXTENSIONS.md).

## Table of Contents

1. [Configuration System](#configuration-system)
2. [HTTP Adapter Dispatch](#http-adapter-dispatch)
3. [Session/Context Lifecycle](#sessioncontext-lifecycle)
4. [Logging](#logging)
5. [Integration Patterns](#integration-patterns)

---

## Configuration System

The IDK provides a hierarchical, multiplatform configuration system with support for multiple property sources and automatic key normalization.

### Configuration Hierarchy

Configuration follows a three-level hierarchy, with each level inheriting from its parent:

```
AppConfigEnvironment (root, level=10)
    └── TenantConfigEnvironment (level=20)
            └── PrincipalConfigEnvironment (level=30)
```

Each level can override properties from its parent. Property resolution starts at the current level and walks up to parents if not found.

### Key Interfaces

#### PropertyResolver

The base interface for property access:

```kotlin
interface PropertyResolver {
    fun containsProperty(key: String): Boolean
    fun <T : Any> getProperty(key: String, targetType: KClass<T>, defaultValue: T? = null): T?
    fun <T : Any> getRequiredProperty(key: String, targetType: KClass<T>, defaultValue: T? = null): T
    fun getPropertyAsString(key: String, defaultValue: String? = null): String?
    fun getRequiredPropertyAsString(key: String, defaultValue: String? = null): String
    fun getAllProperties(): Map<String, Any>
    fun getSubProperties(prefixes: Set<String>, stripPrefix: Boolean = true): Map<String, Any>
}
```

**Usage:**
```kotlin
// Get optional property with default
val timeout = resolver.getProperty("http.timeout", Int::class, 30000)

// Get required property (throws if missing)
val apiKey = resolver.getRequiredPropertyAsString("api.key")

// Get all properties under a prefix
val dbConfig = resolver.getSubProperties(setOf("database."), stripPrefix = true)
// Returns: mapOf("host" to "localhost", "port" to "5432")
```

#### ConfigEnvironment

Extends `PropertyResolver` with environment-specific functionality:

```kotlin
interface ConfigEnvironment : PropertyResolver {
    val parent: ConfigEnvironment?
    val level: ConfigLevel  // APP, TENANT, PRINCIPAL
    fun getActiveProfile(): String
    fun getAppName(): String
    fun getConfigLocation(): Path
    fun getPropertySources(includeParents: Boolean = true): PropertySources

    @Deprecated("Namespace is no longer used for config key construction. Use bare domain prefixes instead.")
    fun getNamespace(): String
}
```

> **Note:** `getNamespace()` is deprecated and should not be used for constructing config key prefixes. All config keys use bare domain prefixes (e.g., `database.host`, `kms.providers.default.type`). See [CONFIGURATION.md](./CONFIGURATION.md) for the correct patterns.

#### ConfigService

Adds property source management:

```kotlin
interface ConfigService : ConfigEnvironment {
    fun addPropertySource(source: PropertySource<*>): ConfigService
    fun removePropertySource(source: PropertySource<*>): ConfigService
    val configLevel: ConfigLevel
}
```

### Property Sources

Property sources provide properties from different origins. They are ordered by priority (lower order value = higher priority):

| Source Type | Order Value | Description |
|-------------|-------------|-------------|
| `ProtectedEnvPropertySource` | HIGHEST (10) | Environment variables with FINAL_/PROTECTED_ support |
| `PropertiesFileAppPropertySource` | LOW (70) | App-level `.properties` files |
| `PropertiesFileTenantPropertySource` | LOW (70) | Tenant-level `.properties` files |
| `PropertiesFilePrincipalPropertySource` | LOW (70) | Principal-level `.properties` files |
| `DefaultAppMapPropertySource` | MEDIUM (50) | Programmatic defaults |

### Property File Support

The IDK provides automatic property file loading similar to Spring Boot. Property files are loaded from the config directory (default: `./config/`).

#### File Naming Convention

**App-level files** (loaded by `PropertiesFileAppPropertySource`):
```
./config/application.properties           # Base app config
./config/application-{profile}.properties # Profile-specific overrides
```

**Tenant-level files** (loaded by `PropertiesFileTenantPropertySource`):
```
./config/tenant/{tenantId}/tenant.properties           # Tenant-specific config
./config/tenant/{tenantId}/tenant-{profile}.properties # Tenant+profile overrides
```

**Principal-level files** (loaded by `PropertiesFilePrincipalPropertySource`):
```
./config/tenant/{tenantId}/principal/{principalId}/principal.properties           # Principal-specific
./config/tenant/{tenantId}/principal/{principalId}/principal-{profile}.properties # Principal+profile
```

#### Example Directory Structure

```
./config/
├── application.properties           # App-wide defaults
├── application-production.properties # Production overrides
└── tenant/
    └── acme-corp/
        ├── tenant.properties        # Tenant-specific config
        ├── tenant-production.properties # Tenant production overrides
        └── principal/
            └── alice/
                ├── principal.properties  # Principal-specific config
                └── principal-production.properties
```

#### Example Properties File

```properties
# application.properties
database.url=jdbc:sqlite:mem:default
database.pool.size=10
log.level=INFO
feature.debug=true
```

```properties
# application-production.properties
database.url=jdbc:postgresql://prod:5432/mydb
database.pool.size=50
log.level=WARN
feature.debug=false
```

#### Key Features

- **Profile overrides**: Profile-specific files override base file properties
- **Missing files are silent**: No errors if property files don't exist
- **Unprefixed keys**: Property keys use bare domain prefixes (e.g., `database.url`, not `sphereon.app.database.url`)
- **Auto-registration**: Property sources register automatically with their config environment
- **Key normalization**: Properties are normalized (e.g., `database.url`, `databaseUrl`, `DATABASE_URL` all work)

#### Property Source Priority

When resolving a property, sources are checked in order (lowest order value first):

1. **Environment variables** (Order.HIGHEST = 10) - Always checked first, highest priority
2. **Property files** (Order.LOW = 70) - Checked after environment
3. **Programmatic defaults** (Order.MEDIUM = 50) - Default map property sources

This means environment variables can always override property file values.

**Creating Custom Property Sources:**
Starter template: [templates/CustomPropertySourceTemplate.kt](./templates/CustomPropertySourceTemplate.kt)

```kotlin
class DatabasePropertySource(
    private val connection: DbConnection
) : PropertySource<DbConnection> {
    override fun getName() = "database"
    override fun getOrder() = Order.MEDIUM.orderValue
    override fun getSource() = connection

    override fun getPropertyAsString(name: String): String? =
        connection.getConfig(name)

    override fun getAllPropertyNames(): Set<String> =
        connection.listConfigKeys()
}
```

### Property Key Normalization

Property keys are automatically normalized for consistency:

| Input | Normalized Output |
|-------|-------------------|
| `httpTimeout` | `http.timeout` |
| `HTTP_TIMEOUT` | `http.timeout` |
| `http-timeout` | `http.timeout` |
| `http timeout` | `http.timeout` |

This allows accessing the same property with different naming conventions:
```kotlin
// All equivalent:
resolver.getProperty("httpTimeout", Int::class)
resolver.getProperty("http.timeout", Int::class)
resolver.getProperty("HTTP_TIMEOUT", Int::class)
```

### Scope-Based Configuration

Configuration is available at different scopes via dependency injection:

```kotlin
// App-scoped configuration (singleton)
@Inject
class MyAppService(private val config: AppConfigEnvironment) {
    val appName = config.getAppName()
}

// Tenant-scoped configuration
@Inject
class MyTenantService(private val config: TenantConfigEnvironment) {
    val tenantSetting = config.getPropertyAsString("tenant.feature.enabled")
}

// Principal-scoped configuration
@Inject
class MyPrincipalService(private val config: PrincipalConfigEnvironment) {
    val userPreference = config.getPropertyAsString("user.theme")
}
```

### Advanced Configuration Features

#### Property Interpolation

The configuration system supports property value interpolation using `${...}` syntax:

```properties
# application.properties
base.url=https://api.example.com
service.endpoint=${base.url}/v1/users
```

**Interpolation Features:**
- Maximum depth: 10 levels (prevents circular references)
- Circular reference detection with error reporting
- Missing property handling (returns original placeholder or error)

#### Secret References

Properties can reference secrets stored in external secret providers:

```properties
# Reference a secret
database.password=${secret:aws/secretsmanager/db-credentials/password}
api.key=${secret:azure/keyvault/my-api-key}
```

**Secret Provider Integration:**
- AWS Secrets Manager: `${secret:aws/secretsmanager/<secret-id>/<key>}`
- Azure Key Vault: `${secret:azure/keyvault/<secret-name>}`
- HashiCorp Vault: `${secret:vault/<path>/<key>}`

**Security Features:**
- Secret values are never logged
- Secrets are resolved lazily on access
- Caching with TTL for performance

#### Profile-Based Configuration

The configuration system supports profile-based property overrides:

```yaml
# config/application.yml
database:
  host: localhost
  port: 5432

# config/application-production.yml
database:
  host: prod-db.example.com
  port: 5432
```

When profile is "production", the system:
1. First loads `application.properties` / `application.yml`
2. Then loads `application-production.properties` / `application-production.yml` (overrides base)

> **Note:** Spring Boot deployments use `sphereon.app.*` prefixes in their own YAML as a Spring adapter convention. IDK property files and YAML files always use bare keys.

#### Database-Backed Configuration

For cloud deployments, configuration can be stored in a database with profile support:

```kotlin
// Settings are stored per-tenant, per-scope, per-profile
val setting = repository.findSetting(
    tenantId = "tenant1",
    scope = ConfigLevel.APP,
    scopeIdentifier = null,
    key = "feature.enabled",
    profile = "production"
)
```

**REST API Endpoints (when lib-conf-settings-persistence-rest is included):**

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/v1/config/settings | List settings |
| GET | /api/v1/config/settings/{key} | Get setting |
| PUT | /api/v1/config/settings/{key} | Set setting |
| DELETE | /api/v1/config/settings/{key} | Delete setting |
| POST | /api/v1/config/refresh | Refresh cache |

#### Cache Architecture

The configuration system includes multi-level caching:

**Property Name Cache:**
- TTL-based expiration (default: 60 seconds)
- Automatic refresh on expiration
- Manual invalidation via `invalidateCache()`

**Value Cache (Database Sources):**
- Per-key caching with TTL
- Negative caching for missing keys
- Cache key format: `{appId}|{profile}|{key}`

### Best Practices

1. **Use typed getters** instead of `getPropertyAsString()` when possible
2. **Provide defaults** for optional configuration
3. **Use `getRequiredProperty()`** for mandatory configuration (fails fast)
4. **Group related properties** under common prefixes
5. **Document all configuration keys** in your module's README
6. **Use secret references** for sensitive values (never hardcode secrets)
7. **Use profiles** to separate environment-specific configuration
8. **Enable caching** for frequently accessed properties

---

## HTTP Adapter Dispatch

The HTTP Adapter system routes incoming HTTP requests to the appropriate handlers using a catalog-driven, tenant-aware dispatcher.

### Architecture Overview

```
GenericHttpRequest
       │
       ▼
┌──────────────────┐
│ HttpAdapterCatalog │  (App-scoped, metadata index)
│ - descriptions    │
│ - diagnostics     │
└──────────────────┘
       │
       ▼
┌──────────────────────┐
│ HttpAdapterDispatcher │  (Session-scoped, routing)
│ - dispatch(request)   │
└──────────────────────┘
       │
       ▼
┌──────────────────────┐
│ HttpAdapter           │  (Handles request)
│ - CommandBacked       │
│ - RoutedHttpAdapter   │
└──────────────────────┘
```

### Key Components

#### HttpAdapterCatalog

App-scoped metadata index of all registered adapters:

```kotlin
interface HttpAdapterCatalog {
    val descriptions: List<HttpAdapterDescription>
    val diagnostics: HttpAdapterCatalogDiagnostics

    fun describeAll(): List<HttpAdapterDescription>
    fun descriptionById(id: String): HttpAdapterDescription?
    fun requireNoCollisions()  // Throws if routing conflicts exist
}
```

#### HttpAdapterDispatcher

Session-scoped routing engine:

```kotlin
interface HttpAdapterDispatcher {
    suspend fun dispatch(request: GenericHttpRequest): GenericHttpResponse
}
```

#### HttpAdapterDescription

Metadata describing an adapter's routing:

```kotlin
data class HttpAdapterDescription(
    val id: String,
    val mount: HttpAdapterMount,
    val endpoints: List<HttpEndpointDescriptor>,
    val openApiHints: OpenApiHints?
)

data class HttpAdapterMount(
    val serverPrefix: String,           // "/api/kms"
    val adapterBasePath: String,        // "/providers"
    val tenantPathMode: TenantPathMode, // OFF, BEFORE_SERVER_PREFIX, AFTER_SERVER_PREFIX, BOTH
    val tenantSegmentPattern: String,   // "/t/{tenantId}"
    val tenantResolutionPriority: TenantResolutionPriority
)
```

### Routing Rules

The dispatcher scores candidates based on:

1. **Server prefix length** (longer = more specific = higher priority)
2. **Base path length** (longer = higher priority)
3. **Endpoint literal segments** (more literals = higher priority)
4. **Total segments** (more = higher priority)
5. **Tenant match** (if applicable)

**Tenant Path Modes:**

| Mode | Example Path | Description |
|------|--------------|-------------|
| `OFF` | `/api/kms/keys` | No tenant in path |
| `BEFORE_SERVER_PREFIX` | `/t/tenant1/api/kms/keys` | Tenant before server prefix |
| `AFTER_SERVER_PREFIX` | `/api/kms/t/tenant1/keys` | Tenant after server prefix |
| `BOTH` | Either placement | Accepts both |

### Implementation Options

#### Option 1: CommandBackedHttpAdapter

Full command integration with extensions and enablement:

```kotlin
@Inject
class MyHttpAdapter(
    execution: SessionExecution
) : CommandBackedHttpAdapter(
    id = "my.http.adapter",
    execution = execution,
    mount = HttpAdapterMount(
        serverPrefix = "/api/myservice",
        adapterBasePath = "/v1",
        tenantPathMode = TenantPathMode.AFTER_SERVER_PREFIX,
        tenantSegmentPattern = "/t/{tenantId}",
        tenantResolutionPriority = TenantResolutionPriority.HEADER_THEN_PATH
    )
) {
    @Inject lateinit var listCommand: ListResourcesEndpoint
    @Inject lateinit var getCommand: GetResourceEndpoint

    override val endpointCommands: List<HttpEndpointCommand>
        get() = listOf(listCommand, getCommand)
}
```

#### Option 2: RoutedHttpAdapter

Lightweight route-based adapter:

```kotlin
class SimpleAdapter : RoutedHttpAdapter() {
    override val id = "simple.adapter"

    override val mount = HttpAdapterMount(
        serverPrefix = "/api/simple",
        adapterBasePath = "",
        tenantPathMode = TenantPathMode.OFF
    )

    override val routes = listOf(
        HttpRoute(HttpMethod.GET, "/health") { request ->
            GenericHttpResponse(200, body = """{"status": "ok"}""")
        }
    )
}
```

### Diagnostics

The catalog provides collision detection at startup:

```kotlin
// Check for routing conflicts
catalog.requireNoCollisions()

// Inspect diagnostics
val diagnostics = catalog.diagnostics
diagnostics.collisions.forEach { collision ->
    when (collision.type) {
        DUPLICATE_ADAPTER_ID -> // Same adapter registered twice
        OVERLAPPING_ENDPOINT -> // Multiple adapters handle same route
        DUPLICATE_ENDPOINT_IN_ADAPTER -> // Same endpoint declared twice
    }
}
```

**Error Responses:**
- `404` - No adapter matches the request
- `500` - Ambiguous routing (multiple adapters with same score)
- `500` - Runtime duplicate adapter instances

### Error Mapping

`CommandBackedHttpAdapter` maps `IdkError` codes to HTTP status:

| IdkError Code | HTTP Status |
|---------------|-------------|
| `NOT_FOUND` | 404 |
| `UNAUTHORIZED` | 401 |
| `FORBIDDEN` | 403 |
| `ILLEGAL_ARGUMENT`, `INVALID` | 400 |
| Others | 500 |

---

## Session/Context Lifecycle

The IDK uses a three-level scope hierarchy for dependency injection and lifecycle management.

### Scope Hierarchy

```
AppScope (Application lifetime, singleton)
    │
    └── UserScope (User/Tenant lifetime)
            │
            └── SessionScope (Request/Session lifetime)
```

### Key Components

#### UserContextManager

Manages user-level contexts:

```kotlin
interface UserContextManager {
    fun getActive(): UserContextInstance       // Never null (returns anonymous)
    fun hasActive(): Boolean                   // True if non-anonymous active
    suspend fun createOrGetFromInputs(tenant: TenantContextData, principal: String, ...): UserContextComponent
    fun activateById(contextId: String): Boolean
    fun destroyById(contextId: String)
    fun getAnonymous(makeActive: Boolean = false): UserContextInstance
    fun getBackgroundService(): UserContextInstance  // NEVER activatable

    val activeInstance: StateFlow<UserContextInstance>  // Reactive updates
}
```

#### SessionContextManager

Manages session-level contexts within a user context:

```kotlin
interface SessionContextManager {
    fun getActive(): SessionInstance           // Never null (returns anonymous)
    suspend fun createOrGetFromId(sessionId: String, makeActive: Boolean = true): SessionComponent
    fun activateById(sessionId: String): Boolean
    fun destroyById(sessionId: String)
    fun getAnonymous(makeActive: Boolean = false): SessionInstance

    val activeInstance: StateFlow<SessionInstance?>  // Reactive updates
}
```

#### SessionExecution

Provides execution context at session level:

```kotlin
interface SessionExecution {
    val sessionContextManager: SessionContextManager
    val sessionContext: SessionContext
    val log: SessionLogService
    val conf: ContextConfig

    fun isAnonymous(): Boolean
}
```

### Lifecycle States

#### User Context Lifecycle

```
App Started
    │
    ▼
Anonymous Context Created (always available)
    │
    ├── User authenticates
    │       │
    │       ▼
    │   createOrGetFromInputs(tenant, principal)
    │       │
    │       ▼
    │   UserContextInstance created
    │       │
    │       ▼
    │   Context activated (if makeActive=true)
    │       │
    │       └── User switches contexts
    │               │
    │               ▼
    │           activateById(contextId)
    │               │
    │               └── ...
    │
    └── Context destroyed
            │
            ▼
        destroyById(contextId)
            │
            ▼
        Scope cleanup, coroutines cancelled
```

#### Session Lifecycle

```
UserContext Active
    │
    ▼
Anonymous Session Available
    │
    ├── New session created
    │       │
    │       ▼
    │   createOrGetFromId(sessionId)
    │       │
    │       ▼
    │   SessionInstance created
    │       │
    │       ▼
    │   Session activated (if makeActive=true)
    │
    └── Session destroyed
            │
            ▼
        destroyById(sessionId)
            │
            ▼
        Scope cleanup
```

### Special Contexts

| Context | ID | Purpose | Can Activate? |
|---------|------|---------|---------------|
| Anonymous User | `<anonymous>:<anonymous>:default` | Default when no user | Yes |
| Anonymous Session | `<anonymous>` | Default within user | Yes |
| Background Service | `<anonymous>:<anonymous>:background-service` | System tasks | **NO** |

**Important:** Background Service can NEVER be made active. Attempts return `false`.

### Thread Safety

The context managers use atomic operations for lock-free reads:

```kotlin
// Fast path: lock-free read
val active = _activeInstance.value

// Slow path: synchronized creation
synchronized(this) {
    // Double-check pattern
    val existing = _instances.value[id]
    if (existing != null) return existing
    // Create new...
}
```

### Usage Examples

**Creating a User Session:**
```kotlin
@Inject
class AuthService(private val userContextManager: UserContextManager) {

    suspend fun onLogin(tenant: TenantContextData, principal: String): SessionInstance {
        val userComponent = userContextManager.createOrGetFromInputs(
            tenant = tenant,
            principal = principal,
            makeActive = true
        )

        // Get the user context instance
        val userContext = userContextManager.getActive()

        // Create a session within that user context
        return userContext.createSession(
            sessionId = UUID.randomUUID().toString(),
            makeActive = true
        )
    }
}
```

**Switching Sessions:**
```kotlin
@Inject
class SessionSwitcher(private val sessionContextManager: SessionContextManager) {

    fun switchToSession(sessionId: String): Boolean {
        return sessionContextManager.activateById(sessionId)
    }

    fun observeActiveSession(): StateFlow<SessionInstance?> {
        return sessionContextManager.activeInstance
    }
}
```

**Cleaning Up:**
```kotlin
@Inject
class CleanupService(
    private val userContextManager: UserContextManager,
    private val sessionContextManager: SessionContextManager
) {
    fun onLogout(userId: String) {
        // Destroy all sessions first
        sessionContextManager.destroyById("<all-session-ids>")

        // Then destroy user context
        userContextManager.destroyById(userId)
    }
}
```

### Error Handling

- `getActive()` never returns null (falls back to anonymous)
- `activateById()` returns `false` if context not found or not activatable
- `destroyById()` is safe to call on non-existent contexts
- Scope destruction cancels all child coroutines

---

## Logging

The IDK provides a multiplatform logging system with scope awareness and async support.

Starter template for custom sinks: [templates/CustomLogServiceTemplate.kt](./templates/CustomLogServiceTemplate.kt)

### Log Levels

```kotlin
enum class LogLevel(val level: Int) {
    TRACE(0),
    DEBUG(10),
    INFO(20),
    WARN(30),
    ERROR(40),
    OFF(100)
}
```

### Key Interfaces

#### LogService (Synchronous)

```kotlin
interface LogService : Logger {
    val sessionContext: SessionContext
    val scope: IdkScope  // APP, USER, SESSION
    val isEnabled: Boolean

    // Configuration
    suspend fun disable()
    suspend fun enable(minLevel: LogLevel)
    suspend fun getConfig(): LoggerConfig
    suspend fun setConfig(config: LoggerConfig): LogService

    // Logging methods
    fun trace(message: String)
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String, errorResult: IdkResult<*, *>? = null)
    fun error(message: String, exception: Throwable? = null, errorResult: IdkResult<*, *>? = null)

    // Convert to async
    fun toAsync(): AsyncLogService
}
```

#### AsyncLogService

All logging methods are `suspend` functions.

#### SessionLogService

Session-scoped logging service:

```kotlin
interface SessionLogService : LogService {
    override val scope: IdkScope  // Always SESSION
    val logManager: SessionLogManager
}
```

### Log Managers

Each scope has a log manager that creates tagged loggers:

```kotlin
interface LogManager {
    suspend fun setGlobalConfig(config: LoggerConfig): LogManager
    suspend fun getGlobalConfig(): LoggerConfig
    fun withTag(tag: String, config: LoggerConfig? = null): LogService
    fun withTagAsync(tag: String, config: LoggerConfig? = null): AsyncLogService
}
```

### Default Implementations

| Implementation | Scope | Behavior |
|----------------|-------|----------|
| `AppConsoleLogServiceImpl` | APP | Prints to console |
| `UserContextConsoleLogServiceImpl` | USER | Prints to console |
| `SessionConsoleLogServiceImpl` | SESSION | Prints to console |
| `AppNoLogService` | APP | No-op (discards logs) |
| `UserContextNoLogService` | USER | No-op |
| `SessionNoLogService` | SESSION | No-op |

### Usage Examples

**Injection-based logging:**
```kotlin
@Inject
class MyService(private val log: SessionLogService) {

    suspend fun process(data: String) {
        log.info("Processing data: $data")

        try {
            // ... work ...
            log.debug("Processing complete")
        } catch (e: Exception) {
            log.error("Processing failed", e)
            throw e
        }
    }
}
```

**Tagged logging via manager:**
```kotlin
@Inject
class ComponentLogger(private val logManager: SessionLogManager) {

    private val log = logManager.withTag("MyComponent")

    fun doWork() {
        log.info("Starting work")  // [MyComponent]: INFO: Starting work
    }
}
```

**Static access for app-level logging:**
```kotlin
// From anywhere (no injection required)
Log.app().withTag("Startup").info("Application starting")
```

**Configuration:**
```kotlin
// Enable with minimum level
log.enable(LogLevel.DEBUG)

// Disable logging
log.disable()

// Custom config
log.setConfig(LoggerConfig(
    minLevel = LogLevel.WARN,
    tag = "custom-tag"
))
```

### Composite Logging

`MultiLogService` distributes logs to multiple backends:

```kotlin
@Inject
class LoggingSetup(
    consoleLogger: SessionConsoleLogServiceImpl,
    fileLogger: FileLogService,
    remoteLogger: RemoteLogService
) {
    val composite = MultiLogService(
        listOf(consoleLogger, fileLogger, remoteLogger)
    )
}
```

---

## Integration Patterns

### Combining Configuration and Logging

```kotlin
@Inject
class ConfigurableService(
    private val config: TenantConfigEnvironment,
    private val log: SessionLogService
) {
    private val logLevel: LogLevel by lazy {
        config.getProperty("service.log.level", String::class, "INFO")
            ?.let { LogLevel.valueOf(it) }
            ?: LogLevel.INFO
    }

    init {
        runBlocking {
            log.enable(logLevel)
        }
    }
}
```

### HTTP Adapter with Session Context

```kotlin
@Inject
class MyEndpoint(
    execution: SessionExecution
) : HttpEndpointCommandAdapter(
    id = "my.service.endpoint.get",
    execution = execution,
    endpoint = HttpEndpointDescriptor(
        method = HttpMethod.GET,
        pathPattern = "/resource/{id}"
    )
) {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        sessionContext: SessionContext
    ): IdkResult<GenericHttpResponse, IdkError> {
        // Access session-scoped services
        execution.log.info("Handling request: ${args.path}")

        // Access configuration
        val timeout = execution.conf.getProperty("request.timeout", Int::class, 5000)

        // Process request...
        return Ok(GenericHttpResponse(200, body = "OK"))
    }
}
```

### Background Processing

```kotlin
@Inject
class BackgroundProcessor(
    private val userContextManager: UserContextManager
) {
    suspend fun runBackgroundTask() {
        // Get background service context (non-activatable)
        val bgContext = userContextManager.getBackgroundService()

        // Create a session for this task
        val session = bgContext.getOrCreateBackgroundServiceSession()

        // Use session for the task
        val execution = session.component as SessionExecution
        execution.log.info("Background task running")

        // Cleanup when done
        // (or let it be reused for next task)
    }
}
```

---

## Summary

| Component | Scope | Purpose |
|-----------|-------|---------|
| `ConfigEnvironment` | App/Tenant/Principal | Hierarchical configuration |
| `PropertyResolver` | Any | Property access |
| `PropertiesFileAppPropertySource` | App | App-level property file loading |
| `PropertiesFileTenantPropertySource` | User | Tenant-level property file loading |
| `PropertiesFilePrincipalPropertySource` | User | Principal-level property file loading |
| `DatabaseAppPropertySource` | App | Database-backed app settings (EDK) |
| `DatabaseTenantPropertySource` | User | Database-backed tenant settings (EDK) |
| `DatabasePrincipalPropertySource` | User | Database-backed principal settings (EDK) |
| `SpringBootAppPropertySource` | App | Spring Boot environment integration |
| `SpringBootTenantPropertySource` | User | Spring Boot tenant settings |
| `SpringBootPrincipalPropertySource` | User | Spring Boot principal settings |
| `HttpAdapterCatalog` | App | HTTP routing metadata |
| `HttpAdapterDispatcher` | Session | HTTP request routing |
| `UserContextManager` | App | User context lifecycle |
| `SessionContextManager` | User | Session lifecycle |
| `SessionExecution` | Session | Execution context |
| `LogService` | Any | Logging |
| `SessionLogService` | Session | Session-scoped logging |

### Configuration Modules

| Module | Location | Description |
|--------|----------|-------------|
| `lib-core-api-public` | IDK | Core configuration interfaces and property sources |
| `lib-conf-settings-persistence-api` | EDK | Database-backed settings API |
| `lib-conf-settings-persistence-postgresql` | EDK | PostgreSQL implementation |
| `lib-conf-settings-persistence-mysql` | EDK | MySQL implementation |
| `lib-conf-settings-persistence-sqlite` | EDK | SQLite implementation |
| `lib-conf-settings-persistence-rest` | EDK | REST API for settings management |
| `lib-conf-azure-app-config` | EDK | Azure App Configuration integration |
| `spring-support` | IDK | Spring Boot configuration integration |

For command-specific documentation, see [ARCHITECTURE.md](./ARCHITECTURE.md) and [CHAINING.md](./CHAINING.md).
