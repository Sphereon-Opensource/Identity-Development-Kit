# Ktor Kotlin-Inject Plugin - Usage Examples

This document provides comprehensive examples of using the Ktor Kotlin-Inject plugin across different platforms and deployment scenarios.

## Table of Contents

1. [Basic Setup](#basic-setup)
2. [JVM Deployment](#jvm-deployment)
3. [GraalVM Native Image](#graalvm-native-image)
4. [JavaScript (Node.js)](#javascript-nodejs)
5. [Service Access Patterns](#service-access-patterns)
6. [Custom Resolvers](#custom-resolvers)

---

## Basic Setup

### 1. Add Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.sphereon.ktor:ktor-server-kotlin-inject:0.25.0-SNAPSHOT")
}
```

### 2. Create Your Components

```kotlin
import me.tatarka.inject.annotations.Component
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@Component
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class MyAppComponent(
    @get:Provides val application: Unit,
    @get:Provides val appId: String,
    @get:Provides val profile: String,
    @get:Provides val version: String,
) : AppComponent
```

### 3. Configure Ktor Application

```kotlin
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.sphereon.ktor.server.inject.KotlinInjectPlugin

fun main() {
    embeddedServer(Netty, port = 8080) {
        // Initialize your AppComponent
        val appComponent = MyAppComponent.create(
            application = Unit,
            appId = "my-app",
            profile = "production",
            version = "1.0.0"
        )

        // Install the KotlinInject plugin
        install(KotlinInjectPlugin) {
            this.appComponent = appComponent
        }

        // Define your routes
        routing {
            get("/") {
                call.respondText("Hello, Kotlin-Inject!")
            }
        }
    }.start(wait = true)
}
```

---

## JVM Deployment

### Standard JAR

```kotlin
// build.gradle.kts
plugins {
    application
    kotlin("jvm")
}

application {
    mainClass.set("com.example.ApplicationKt")
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "com.example.ApplicationKt"
        }
        // Create fat JAR
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
```

**Build and Run:**

```bash
./gradlew jar
java -jar build/libs/my-app.jar
```

### With Ktor Auth (JVM-specific)

```kotlin
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureAuth() {
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JWTVerifier.create())
            validate { credential ->
                JWTPrincipal(credential.payload)
            }
        }
    }

    install(KotlinInjectPlugin) {
        appComponent = myAppComponent
        
        // Use JWT principal resolver (JVM only)
        principalResolver = JwtPrincipalResolver()
    }
}
```

---

## GraalVM Native Image

GraalVM Native Image compiles your application to a native executable with instant startup and lower memory footprint.

### 1. Configure Build

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm")
    id("org.graalvm.buildtools.native") version "0.10.2"
}

graalvmNative {
    binaries {
        named("main") {
            // Main class
            mainClass.set("com.example.ApplicationKt")
            
            // Build options
            buildArgs.add("--no-fallback")
            buildArgs.add("--initialize-at-build-time=kotlinx.coroutines,kotlin")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            
            // Resource configuration
            buildArgs.add("-H:ResourceConfigurationFiles=src/main/resources/native-image/resource-config.json")
            
            // Reflection configuration (required for kotlin-inject)
            buildArgs.add("-H:ReflectionConfigurationFiles=src/main/resources/native-image/reflect-config.json")
            
            // Optimization
            buildArgs.add("-O3")
            buildArgs.add("--gc=G1")
        }
    }
}
```

### 2. Create Reflection Configuration

Since kotlin-inject uses code generation, minimal reflection is needed. However, Ktor may require some:

```json
// src/main/resources/native-image/reflect-config.json
[
  {
    "name": "kotlin.reflect.jvm.internal.KClassImpl",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true
  },
  {
    "name": "io.ktor.server.netty.NettyApplicationEngine",
    "allDeclaredConstructors": true
  }
]
```

### 3. Resource Configuration

```json
// src/main/resources/native-image/resource-config.json
{
  "resources": {
    "includes": [
      {
        "pattern": ".*\\.conf$"
      },
      {
        "pattern": ".*\\.properties$"
      },
      {
        "pattern": "application\\.conf$"
      }
    ]
  }
}
```

### 4. Application Code

```kotlin
package com.example

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.getAppService
import com.sphereon.core.api.conf.AppConfigEnvironment

fun main() {
    println("Starting Native Image Application...")
    
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureKotlinInject()
        configureRouting()
    }.start(wait = true)
}

fun Application.configureKotlinInject() {
    // Initialize AppComponent
    val appComponent = MyAppComponent.create(
        application = Unit,
        appId = "native-app",
        profile = System.getenv("APP_PROFILE") ?: "production",
        version = "1.0.0"
    )

    // Install KotlinInject plugin
    install(KotlinInjectPlugin) {
        this.appComponent = appComponent
    }
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello from GraalVM Native Image!")
        }
        
        get("/config") {
            // Access service via plugin
            val config = call.getAppService<AppConfigEnvironment>()
            call.respondText("App: ${config.getAppName()}, Profile: ${config.getActiveProfile()}")
        }
        
        get("/health") {
            call.respondText("OK")
        }
    }
}
```

### 5. Build Native Image

```bash
# Build with Gradle
./gradlew nativeCompile

# The native executable will be at:
# build/native/nativeCompile/my-app

# Run it
./build/native/nativeCompile/my-app
```

### 6. Docker Deployment (Multi-stage)

```dockerfile
# Stage 1: Build with GraalVM
FROM ghcr.io/graalvm/native-image:22-ol9 AS build

WORKDIR /build

# Copy source
COPY . .

# Build native image
RUN ./gradlew nativeCompile --no-daemon

# Stage 2: Runtime (minimal)
FROM debian:stable-slim

WORKDIR /app

# Copy native executable
COPY --from=build /build/build/native/nativeCompile/my-app /app/

# Expose port
EXPOSE 8080

# Run
CMD ["/app/my-app"]
```

**Build and run:**

```bash
docker build -t my-app-native .
docker run -p 8080:8080 my-app-native
```

### Performance Comparison

| Metric | JVM | GraalVM Native |
|--------|-----|----------------|
| Startup Time | ~2-3 seconds | ~0.01 seconds (100x faster) |
| Memory (idle) | ~150 MB | ~30 MB (5x less) |
| Binary Size | ~50 MB (JAR) | ~40 MB (native) |
| Throughput | High | Similar |

---

## JavaScript (Node.js)

The plugin is fully multiplatform and works on JavaScript/Node.js.

### Build Configuration

```kotlin
// build.gradle.kts
kotlin {
    js {
        nodejs {
            binaries.executable()
        }
    }
    
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation("com.sphereon.ktor:ktor-server-kotlin-inject:0.25.0-SNAPSHOT")
                implementation("io.ktor:ktor-server-core:3.3.3")
            }
        }
    }
}
```

### Application Code

```kotlin
// src/jsMain/kotlin/Application.kt
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.sphereon.ktor.server.inject.KotlinInjectPlugin

fun main() {
    embeddedServer(CIO, port = 8080) {
        val appComponent = MyAppComponent.create(
            application = Unit,
            appId = "js-app",
            profile = "development",
            version = "1.0.0"
        )

        install(KotlinInjectPlugin) {
            this.appComponent = appComponent
        }

        routing {
            get("/") {
                call.respondText("Hello from Node.js!")
            }
        }
    }.start(wait = true)
}
```

### Run

```bash
./gradlew jsNodeRun
```

---

## Service Access Patterns

### 1. App-Scoped Services

```kotlin
routing {
    get("/app-service") {
        // Access app-scoped service
        val config = call.getAppService<AppConfigEnvironment>()
        val logManager = call.getAppService<AppLogManager>()
        
        logManager.withTag("APP").info("Accessed app config")
        call.respondText("App: ${config.getAppName()}")
    }
}
```

### 2. User-Scoped Services

```kotlin
routing {
    get("/user-service") {
        // Access user-scoped service
        val userLogManager = call.getUserService<UserContextLogManager>()
        val userInstance = call.userInstance
        
        userLogManager.withTag("USER").info("User accessed endpoint")
        call.respondText("User: ${userInstance.userContext.principal}")
    }
}
```

### 3. Session-Scoped Services

```kotlin
routing {
    get("/session-service") {
        // Access session-scoped service
        val sessionLogManager = call.getSessionService<SessionLogManager>()
        val sessionInstance = call.sessionInstance
        
        sessionLogManager.withTag("SESSION").info("Session activity")
        call.respondText("Session: ${sessionInstance.sessionId}")
    }
}
```

### 4. Direct Component Access

```kotlin
routing {
    get("/direct-access") {
        // Direct component access
        val appComponent = call.appComponent
        val userInstance = call.userInstance
        val sessionInstance = call.sessionInstance
        
        // Access services directly from components
        val config = appComponent.appConfigEnvironment
        val userLog = userInstance.component.logManager
        
        call.respondText("Direct access works!")
    }
}
```

---

## Custom Resolvers

### Custom Tenant Resolver

```kotlin
class CustomTenantResolver : TenantResolver {
    override suspend fun resolve(call: ApplicationCall): String? {
        // Extract tenant from subdomain
        val host = call.request.headers["Host"]
        return host?.split(".")?.firstOrNull()
    }
}

install(KotlinInjectPlugin) {
    appComponent = myAppComponent
    tenantResolver = CustomTenantResolver()
}
```

### Custom Principal Resolver

```kotlin
class ApiKeyPrincipalResolver : PrincipalResolver {
    override suspend fun resolve(call: ApplicationCall): String? {
        // Extract principal from API key
        val apiKey = call.request.headers["X-API-Key"]
        return validateApiKey(apiKey)
    }
}

install(KotlinInjectPlugin) {
    appComponent = myAppComponent
    principalResolver = ApiKeyPrincipalResolver()
}
```

---

## Environment-Specific Configuration

### Development

```kotlin
fun Application.configureDevelopment() {
    install(KotlinInjectPlugin) {
        appComponent = MyAppComponent.create(
            application = Unit,
            appId = "my-app",
            profile = "development",
            version = "1.0.0-SNAPSHOT"
        )
        
        // Development-specific settings
        tenantResolver = StaticTenantResolver("dev-tenant")
    }
}
```

### Production

```kotlin
fun Application.configureProduction() {
    install(KotlinInjectPlugin) {
        appComponent = MyAppComponent.create(
            application = Unit,
            appId = "my-app",
            profile = "production",
            version = "1.0.0"
        )
        
        // Production resolvers from headers
        tenantResolver = HeaderTenantResolver()
        principalResolver = JwtPrincipalResolver()
    }
}
```

---

## Testing

### Unit Tests

```kotlin
class MyServiceTest {
    @Test
    fun testWithKotlinInject() = testApplication {
        val appComponent = TestAppComponent.create(
            application = Unit,
            appId = "test",
            profile = "test",
            version = "1.0.0"
        )

        application {
            install(KotlinInjectPlugin) {
                this.appComponent = appComponent
            }
        }

        client.get("/test").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}
```

---

## Troubleshooting

### GraalVM Issues

**Problem:** `ClassNotFoundException` at runtime
**Solution:** Add missing classes to reflection configuration

**Problem:** Resources not found
**Solution:** Add resource patterns to `resource-config.json`

**Problem:** Slow startup
**Solution:** Use `--initialize-at-build-time` for more classes

### General Issues

**Problem:** Service not found
**Solution:** Ensure service is annotated with `@ContributesBinding` and proper scope

**Problem:** Context not resolved
**Solution:** Verify tenant/principal headers are sent in requests

---

## Best Practices

1. **Use DI for everything** - Don't create services manually
2. **Scope services correctly** - Use AppScope/UserScope/SessionScope appropriately
3. **Test with actual components** - Don't mock the DI framework
4. **Profile your native image** - Use GraalVM's profiling tools
5. **Version control your configs** - Keep reflection configs in source control

---

## Additional Resources

- [Kotlin-Inject Documentation](https://github.com/evant/kotlin-inject)
- [Ktor Documentation](https://ktor.io/docs/)
- [GraalVM Native Image](https://www.graalvm.org/native-image/)
- [IDK Architecture Guide](../../../README.md)
