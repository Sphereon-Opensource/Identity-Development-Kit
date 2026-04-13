# Sphereon IDK Ktor Server - Kotlin Inject Plugin

Ktor server plugin that provides kotlin-inject, kotlin-inject-anvil (Amazon), and Amazon App Platform injection support for the Identity Development Kit (IDK).

## Overview

This plugin enables the three-scope architecture used throughout the IDK:

- **AppScope**: Singleton instances shared across the entire application
- **UserScope**: Principal and tenant scoped instances (per user/tenant combination)
- **SessionScope**: Session scoped instances (per request/response in REST APIs)

**Multiplatform Support:**  
Compatible with JVM, JavaScript, Native, and GraalVM targets. Designed to work with Ktor CIO engine for maximum portability.

## Key Features

- **Component Inspection Service Access**: Services accessed via reflection inspection of kotlin-inject components (fully multiplatform)
- **Request-Scoped Context Resolution**: Automatically resolves tenant, principal, and session for each request
- **Extension Functions**: Convenient access to services in routes via extension functions
- **Thread-Safe**: Per-request isolation ensures thread safety
- **Flexible Configuration**: Customizable tenant and principal resolvers
- **Multiplatform Compatible**: Works on JVM, JavaScript, Native, and GraalVM
- **CIO Engine Support**: Designed for Ktor's multiplatform CIO engine

## Installation

### 1. Add Dependency

```kotlin
dependencies {
    implementation(projects.ktor.server.plugins.ktorServerKotlinInject)
}
```

### 2. Configure Plugin

```kotlin
import com.sphereon.ktor.server.inject.KotlinInject
import io.ktor.server.application.*

fun Application.module() {
    install(KotlinInjectPlugin) {
        // Required: Provide your AppComponent
        appComponent = MyAppComponent.create(...)

        // Optional: Custom resolvers
        tenantResolver = MyTenantResolver()
        principalResolver = MyPrincipalResolver()
        
        // Optional: Configure default header-based resolvers
        tenantHeader = "X-Tenant-ID"
        principalHeader = "X-User-ID"
    }
}
```

## Usage in Routes

### Accessing Services

```kotlin
routing {
    get("/api/resource") {
        // Access app-scoped services
        val appService = call.getAppService<MyAppService>()
        val config = call.appComponent.appConfigEnvironment
        
        // Access user-scoped services
        val userService = call.getUserService<MyUserService>()
        
        // Access session-scoped services
        val sessionService = call.getSessionService<MySessionService>()
        
        // Access instances directly
        val userInstance = call.userInstance
        val tenant = userInstance.userContext.tenant
        val principal = userInstance.userContext.principal
        
        val sessionInstance = call.sessionInstance
        val sessionId = sessionInstance.sessionId
        
        call.respond(mapOf(
            "tenant" to tenant,
            "principal" to principal,
            "sessionId" to sessionId
        ))
    }
}
```

### Extension Functions

The plugin provides these extension functions on `ApplicationCall`:

- `call.appComponent: AppComponent` - Access the root AppComponent
- `call.userInstance: UserContextInstance` - Access the user context instance
- `call.sessionInstance: SessionInstance` - Access the session instance
- `call.requestContext: RequestScopedContext` - Access the complete request context
- `call.getAppService<T>(): T` - Get an app-scoped service
- `call.getUserService<T>(): T` - Get a user-scoped service
- `call.getSessionService<T>(): T` - Get a session-scoped service

## Custom Resolvers

### Custom Tenant Resolver

```kotlin
class JwtTenantResolver : TenantResolver {
    override fun resolve(call: ApplicationCall): TenantInput {
        val jwt = extractJwt(call)
        val tenantId = jwt.getClaim("tenant_id")
        return DefaultTenantInputString(tenantId)
    }
}

// Use in configuration
install(KotlinInjectPlugin) {
    appComponent = myAppComponent
    tenantResolver = JwtTenantResolver()
}
```

### Custom Principal Resolver

```kotlin
class JwtPrincipalResolver : PrincipalResolver {
    override fun resolve(call: ApplicationCall): PrincipalInput {
        val jwt = extractJwt(call)
        val userId = jwt.getClaim("sub")
        return DefaultPrincipalInputString(userId)
    }
}

// Use in configuration
install(KotlinInjectPlugin) {
    appComponent = myAppComponent
    principalResolver = JwtPrincipalResolver()
}
```

## Service Access via Component Inspection

The plugin accesses services through **component inspection** using Kotlin reflection. This approach is **fully multiplatform compatible** and works on JVM, JavaScript, Native, and
GraalVM.

### How it Works

When you call `call.getAppService<T>()`, `call.getUserService<T>()`, or `call.getSessionService<T>()`, the plugin:

1. First checks the scope's service registry (if the service was registered manually)
2. Then uses Kotlin reflection to inspect the component for:
    - Properties that match the requested type
    - Zero-parameter functions (provider methods) that return the requested type

This means services are accessible as long as they're exposed as **properties or provider methods** on the kotlin-inject component.

### Example Service

```kotlin
// Define service interface
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
interface MyUserService {
    fun doSomething(): String
}

// Implementation will be injected by kotlin-inject
@Inject
class MyUserServiceImpl(
    private val userContext: UserContext
) : MyUserService {
    override fun doSomething(): String {
        return "Hello ${userContext.principal}"
    }
}

// The service is automatically accessible in components via kotlin-inject
// No additional registration needed - component inspection finds it!
```

## Architecture

### Request Flow

1. **Plugin Installation**: Plugin installs an interceptor in the Ktor pipeline
2. **Request Processing**: For each request:
    - Tenant is resolved via `TenantResolver`
    - Principal is resolved via `PrincipalResolver`
    - User context is created or retrieved (ID-based, no active state)
    - Session is created with a unique ID
    - Context is stored as a call attribute
3. **Route Execution**: Routes access services via extension functions
4. **Service Resolution**: Services are lazily resolved from the appropriate scope

### Thread Safety

- Each request has its own `RequestScopedContext` stored as a call attribute
- No global state is modified
- Context resolution is ID-based (never sets `makeActive = true`)
- Thread-safe by design through request isolation

## Comparison with Spring Boot Support

The Ktor plugin provides similar functionality to the Spring Boot support project:

| Feature            | Spring Boot                           | Ktor                                |
|--------------------|---------------------------------------|-------------------------------------|
| Scope Architecture | ✅ Same (App/User/Session)             | ✅ Same (App/User/Session)           |
| Service Discovery  | ✅ BeanDefinitionRegistryPostProcessor | ✅ Component Inspection (Reflection) |
| Context Resolution | ✅ Servlet Filter                      | ✅ Ktor Interceptor                  |
| Service Access     | ✅ Dependency Injection                | ✅ Extension Functions               |
| Custom Resolvers   | ✅ Resolver Interfaces                 | ✅ Resolver Interfaces               |
| Thread Safety      | ✅ Request-scoped beans                | ✅ Call-scoped attributes            |
| Multiplatform      | ❌ JVM only                            | ✅ JVM, JS, Native, GraalVM          |

### Key Differences

1. **Service Access**:
    - Spring: Services are injected into controllers/components
    - Ktor: Services are accessed via extension functions in routes

2. **Configuration**:
    - Spring: Auto-configuration with `@EnableSphereonRestApi`
    - Ktor: Manual plugin installation with `install(KotlinInjectPlugin)`

3. **Scope Management**:
    - Spring: Custom Spring scopes delegate to kotlin-inject
    - Ktor: Direct access to kotlin-inject scopes via extension functions

## Configuration Options

```kotlin
class KotlinInjectConfiguration {
    var appComponent: AppComponent? = null       // Required: The root kotlin-inject component
    var tenantHeader: String = "X-Tenant-ID"     // Default header for tenant resolution
    var principalHeader: String = "X-User-ID"    // Default header for principal resolution
    var tenantResolver: TenantResolver           // Custom tenant resolver (optional)
    var principalResolver: PrincipalResolver     // Custom principal resolver (optional)
}
```

## Multiplatform Usage

The plugin is **fully multiplatform compatible** and works identically on JVM, JavaScript, Native, and GraalVM. Service access via extension functions (`getAppService<T>()`,
`getUserService<T>()`, `getSessionService<T>()`) uses Kotlin reflection to inspect components, which is available on all platforms.

### Accessing Services (All Platforms)

```kotlin
routing {
    get("/api/data") {
        // Option 1: Use extension functions (recommended, works on all platforms)
        val appConfig = call.getAppService<AppConfigEnvironment>()
        val userLogManager = call.getUserService<UserContextLogManager>()
        val sessionLogManager = call.getSessionService<SessionLogManager>()
        
        // Option 2: Access directly from component (also works on all platforms)
        val appComponent = call.appComponent
        val userInstance = call.userInstance
        val sessionInstance = call.sessionInstance
        
        call.respond(mapOf(
            "appName" to appConfig.getAppName(),
            "tenant" to userInstance.context.tenant,
            "sessionId" to sessionInstance.sessionId
        ))
    }
}
```

### Platform Notes

- **JVM**: Full reflection support, all features work
- **JavaScript**: Kotlin reflection available, all extension functions work
- **Native**: Kotlin reflection available (with opt-in), all extension functions work
- **GraalVM**: Reflection configuration may be needed for native image builds (see below)

## GraalVM Native Image Support

The plugin fully supports GraalVM Native Image for creating native executables with instant startup and low memory footprint.

### Quick Start

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm")
    id("org.graalvm.buildtools.native") version "0.10.2"
}

graalvmNative {
    binaries {
        named("main") {
            mainClass.set("com.example.ApplicationKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("--initialize-at-build-time=kotlinx.coroutines,kotlin")
        }
    }
}
```

### Reflection Configuration

Since kotlin-inject uses **code generation instead of reflection**, minimal reflection configuration is needed. The plugin primarily uses Kotlin reflection for component
inspection, which is mostly compile-time.

**Minimal Configuration** (`src/main/resources/META-INF/native-image/reflect-config.json`):

```json
[
  {
    "name": "kotlin.reflect.jvm.internal.KClassImpl",
    "allDeclaredMethods": true,
    "allPublicMethods": true
  }
]
```

### Build and Run

```bash
# Build native image
./gradlew nativeCompile

# Run native executable
./build/native/nativeCompile/my-app

# Performance: ~10ms startup (vs ~2-3s for JVM)
```

### Docker Deployment

```dockerfile
FROM ghcr.io/graalvm/native-image:22-ol9 AS build
WORKDIR /build
COPY . .
RUN ./gradlew nativeCompile --no-daemon

FROM debian:stable-slim
COPY --from=build /build/build/native/nativeCompile/my-app /app/
EXPOSE 8080
CMD ["/app/my-app"]
```

**Benefits:**

- **100x faster startup**: ~10ms vs ~2-3 seconds
- **5x less memory**: ~30 MB vs ~150 MB idle
- **Smaller images**: ~40 MB native vs ~50 MB JAR
- **No JVM overhead**: Direct native execution

For complete GraalVM examples and configuration, see [EXAMPLE.md](./EXAMPLE.md#graalvm-native-image).

## Examples and Documentation

- **[EXAMPLE.md](./EXAMPLE.md)** - Comprehensive examples for JVM, GraalVM, JavaScript, and more
- **[MULTIPLATFORM_IMPLEMENTATION.md](./MULTIPLATFORM_IMPLEMENTATION.md)** - Detailed multiplatform architecture documentation

## License

© 2025 Sphereon International B.V.

Licensed under the Apache License, Version 2.0
