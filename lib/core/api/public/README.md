# IDK Core API Public

The public API module for the Identity Development Kit (IDK). This module provides the command pattern infrastructure, error handling, and core interfaces used throughout the IDK/EDK/VDX stack.

## Features

- **Command Pattern** - Extensible command infrastructure with lifecycle hooks
- **Result Monad** - `IdkResult<V, E>` for type-safe error handling
- **Command Composition** - Chain, pipe, saga, and chain-of-responsibility patterns
- **Authorization** - Pattern-based command authorization
- **Audit Logging** - Structured audit events for command execution
- **Caching** - Multi-backend caching with tenant/principal partitioning
- **Multiplatform** - Kotlin Multiplatform support (JVM, JS, iOS, Linux)

## Quick Start

### Creating a Command

```kotlin
// Using DSL
val parseCommand = command<String, ParsedData>("data.parser.document.parse") {
    subsystem(EventSubsystems.DATA)
    supports { args, _ -> args is String && args.isNotBlank() }
    execute { input, ctx ->
        val result = parser.parse(input)
        Ok(result)
    }
}

// Using SimpleCommand (no SessionContext needed)
val validateCommand = simpleCommand<ParsedData, Boolean>("data.validator.document.validate") { data ->
    if (data.isValid) Ok(true)
    else Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid data"))
}
```

### Chaining Commands

```kotlin
val pipeline = pipe("data.processor.document.process", parseCommand)
    .then(validateCommand.asCommand())
    .thenMapSimple { if (it) "Valid" else "Invalid" }
    .build()

val result = pipeline.execute(input, sessionContext)
```

### Authorization

```kotlin
val authorizer = PatternCommandAuthorizer.fromPatterns(
    "data.parser.**",
    "data.validator.**"
)

val authExtension = AuthorizationExtension(authorizer)
```

### Audit Logging

```kotlin
val auditExtension = AuditableCommandExtension<MyArgs, MyResult, IdkError>(
    eventSink = myAuditSink
)
```

## Module Structure

```
lib-core-api-public/
├── docs/
│   ├── ARCHITECTURE.md     # Command hierarchy and extension points
│   ├── CHAINING.md         # Command composition patterns
│   ├── AUTHORIZATION.md    # Pattern-based authorization
│   ├── AUDIT-LOGGING.md    # Audit event infrastructure
│   ├── CACHING.md          # Multi-backend caching infrastructure
│   ├── CONFIGURATION.md    # Configuration system
│   └── CORE-CONCEPTS.md    # Config, HTTP, Session, Logging
├── src/
│   └── commonMain/kotlin/com/sphereon/core/api/
│       ├── cache/
│       │   ├── CacheBackend.kt         # Backend abstraction
│       │   ├── CacheInfrastructure.kt  # Core types (ScopedKey, etc.)
│       │   ├── CacheManager.kt         # Cache manager interface
│       │   ├── CacheModule.kt          # DI module
│       │   ├── CacheSerializer.kt      # Serialization
│       │   ├── CacheService.kt         # High-level service
│       │   ├── DefaultCacheManager.kt  # Manager implementation
│       │   ├── LayeredCacheBackend.kt  # Read/write-through
│       │   └── ScopedCacheImpl.kt      # Scoped cache implementation
│       ├── session/
│       │   ├── Command.kt              # Core command interfaces
│       │   ├── CommandId.kt            # Hierarchical command IDs
│       │   ├── CommandDsl.kt           # DSL for command creation
│       │   ├── SimpleCommand.kt        # Simplified command interface
│       │   ├── ChainedCommand.kt       # Sequential chaining
│       │   ├── PipeBuilder.kt          # Pipeline builder
│       │   ├── ChainOfResponsibility.kt
│       │   ├── SagaCommand.kt          # Compensatable transactions
│       │   ├── CommandComposition.kt   # Functional composition
│       │   ├── CommandAuthorization.kt # Authorization interfaces
│       │   ├── PatternCommandAuthorizer.kt
│       │   ├── PolicyDecisionProvider.kt
│       │   ├── AuditEvent.kt           # Audit event schema
│       │   ├── AuditEventSink.kt       # Sink interface
│       │   ├── AuditableCommandExtension.kt
│       │   ├── EnhancedCommandExtension.kt
│       │   └── testing/
│       │       └── CommandTestSupport.kt
│       ├── error/
│       │   └── Error.kt                # IdkError definitions
│       └── IdkResult.kt                # Result monad
```

## Key Concepts

### Command IDs

Commands use hierarchical IDs for pattern-based authorization:

```
<domain>.<capability>.<resource>.<action>[.<variant>]
```

Examples:
- `did.manager.did.resolve`
- `openid.oid4vp.verifier.request.create`
- `data.store.party.read`

### IdkResult

Always use `IdkResult<V, E>` instead of Kotlin's `Result<T>`:

```kotlin
import com.sphereon.core.api.Ok
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult

fun process(): IdkResult<String, IdkError> {
    return if (success) Ok("result")
    else Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed"))
}

// Check result
if (result.isOk) println(result.value)
if (result.isErr) println(result.error.message)
```

### Extensions

Commands support execution extensions:

```kotlin
interface IEnhancedCommandExecutionExtension<Arg, SuccessResult, ErrorResult> {
    suspend fun beforeExecute(...): BeforeExecuteResult<...>
    suspend fun duringExecute(...): Arg
    suspend fun afterExecute(...): IdkResult<...>
}
```

## Documentation

- [ARCHITECTURE.md](docs/ARCHITECTURE.md) - Command hierarchy, scopes, extension points
- [CACHING.md](docs/CACHING.md) - Multi-backend caching with tenant partitioning
- [CHAINING.md](docs/CHAINING.md) - Chain, pipe, saga, chain-of-responsibility patterns
- [AUTHORIZATION.md](docs/AUTHORIZATION.md) - Pattern matching and access control
- [AUDIT-LOGGING.md](docs/AUDIT-LOGGING.md) - Audit event schema and EDK/VDX integration
- [CONFIGURATION.md](docs/CONFIGURATION.md) - Configuration system
- [CORE-CONCEPTS.md](docs/CORE-CONCEPTS.md) - HTTP dispatch, session lifecycle
- [API-SURFACE.md](docs/API-SURFACE.md) - Canonical APIs to use for new integration code
- [EXTENSIONS.md](docs/EXTENSIONS.md) - Starter templates for config, logging, and service commands

## Testing

Test utilities are provided in the `testing` package:

```kotlin
import com.sphereon.core.api.session.testing.CommandTestSupport.testContext
import com.sphereon.core.api.session.testing.CommandTestSupport.testExecute
import com.sphereon.core.api.session.testing.mockCommand
import com.sphereon.core.api.session.testing.assertOkOrFail

// Execute with test context
val result = myCommand.testExecute(args)

// Create mock commands
val mock = mockCommand<String, Int>("test.mock") { args, _ -> Ok(args.length) }

// Assert results
val value = result.assertOkOrFail("Expected success")
```

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

© 2026 Sphereon International B.V.
