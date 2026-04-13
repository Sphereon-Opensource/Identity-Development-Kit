# Command Chaining and Composition

This document describes the various patterns for combining and composing commands in the IDK Core API.

## Overview

Commands can be combined in several ways:

| Pattern | Use Case | Function |
|---------|----------|----------|
| **Chain** | Sequential execution, output → input | `chain()` |
| **Pipe** | Multi-step pipeline with transformations | `pipe()` |
| **Chain of Responsibility** | Multiple handlers, first success | `chainOfResponsibility()` |
| **Saga** | Compensatable transactions | `saga()` |
| **Composition** | Transform input/output/error | `mapInput()`, `mapOutput()`, etc. |

## Chain Pattern

The `chain()` function connects two commands where the output of the first becomes the input of the second.

```kotlin
// Basic chaining
val parseAndValidate = parseCommand.chain(
    "domain.capability.resource.parse-and-validate",
    validateCommand
)

// With custom error mapper
val parseAndValidate = parseCommand.chain(
    "domain.capability.resource.parse-and-validate",
    validateCommand,
    customErrorMapper
)
```

### ChainedCommand Behavior

```kotlin
class ChainedCommand<FirstArg, Intermediate, FinalResult, E> : Command<FirstArg, FinalResult, E> {
    // 1. Execute first command
    // 2. Validate second command supports the OUTPUT (not the original input!)
    // 3. Execute second command with intermediate result
}
```

**Important**: Unlike the legacy `andThen()`, `chain()` validates `supports()` on the OUTPUT of the first command.

### Chain Multiple Commands

```kotlin
val pipeline = chainAll<Input, Output, IdkError>(
    "domain.capability.resource.full-pipeline",
    listOf(parseCmd, validateCmd, transformCmd, storeCmd),
    IdkErrorCommandErrorMapper
)
```

## Pipe Pattern

The `PipeBuilder` provides a fluent API for building command pipelines with type safety:

```kotlin
val pipeline = pipe("data.processor.document.transform", parseCommand)
    .then(validateCommand)                    // Chain another command
    .thenMapSimple { it.copy(validated = true) }  // Transform result
    .filter(                                   // Filter results
        predicate = { it.isValid },
        onFailure = { IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid document") }
    )
    .tap { result, ctx -> log.info("Processed: $result") }  // Side effect
    .build()
```

### PipeBuilder Methods

| Method | Description |
|--------|-------------|
| `then(command)` | Chain another command |
| `thenMap(fn)` | Transform with `IdkResult` |
| `thenMapSimple(fn)` | Simple transformation |
| `filter(predicate, onFailure)` | Filter results |
| `tap(effect)` | Side effect without changing result |
| `build()` | Create the final Command |

### Transform Commands

Create inline transformation steps:

```kotlin
// Full transformation
val transform = transformCommand<Input, Output, IdkError>("my.transform") { input ->
    Ok(doTransform(input))
}

// Simple (no context)
val transform = transformCommand<Input, Output>("my.transform") { input ->
    doTransform(input)
}
```

## Chain of Responsibility Pattern

When multiple handlers can process a request, use Chain of Responsibility:

```kotlin
val didResolver = chainOfResponsibility(
    "did.manager.did.resolve",
    listOf(
        webDidResolver,      // Tries web:did first
        ionDidResolver,      // Falls back to ion:did
        keyDidResolver       // Falls back to key:did
    )
)
```

### Behavior

1. Try each handler in order
2. If handler returns `true` from `supports()`:
   - Execute it
   - If `Ok`, return the result
   - If `Err`, collect the error and try next handler
3. If all handlers fail, return combined error

### Variants

```kotlin
// First handler that supports AND succeeds
val resolver = firstSuccess("did.resolve", handlers, errorMapper)

// First handler that supports, regardless of result
val handler = firstMatch("process.request", handlers, errorMapper)

// Custom configuration
val custom = chainOfResponsibilityConfigured(
    "custom.handler",
    handlers,
    errorMapper,
    ChainOfResponsibilityConfig(
        stopOnFirstSupporting = false,  // Continue trying on failure
        collectAllErrors = true          // Aggregate all errors
    )
)
```

## Saga Pattern

For operations that need compensation (rollback) on failure:

```kotlin
// Create a saga using the builder
val orderSaga = sagaBuilder<CreateOrderArgs, Order, IdkError>("order.saga.create")
    .step(createOrderCommand)       // Step 1: Create order
    .step(reserveInventoryCommand)  // Step 2: Reserve inventory
    .step(chargePaymentCommand)     // Step 3: Charge payment
    .build()
```

### CompensatableCommand

Commands that participate in sagas implement `CompensatableCommand`:

```kotlin
interface CompensatableCommand<Arg, Result, E> : Command<Arg, Result, E> {
    suspend fun compensate(
        args: Arg,
        result: Result,
        sessionContext: SessionContext
    ): IdkResult<Unit, E>
}
```

### Inline Compensatable Commands

```kotlin
val reserveInventory = compensatable<ReserveArgs, Reservation, IdkError>(
    id = "inventory.reservation.create",
    executeFn = { args ->
        // Reserve inventory
        Ok(reservation)
    },
    compensateFn = { args, reservation ->
        // Release reservation
        releaseReservation(reservation.id)
        Ok(Unit)
    }
)
```

### Saga Execution Flow

```
Step 1 → Success → Step 2 → Success → Step 3 → Success → Return Ok(result)
  │                  │                  │
  │                  │                  └── Failure → Compensate 2, 1 → Return Err
  │                  └── Failure → Compensate 1 → Return Err
  └── Failure → Return Err
```

## Functional Composition

Transform commands without modifying their implementation:

### Input Transformation

```kotlin
// Full transformation with session context and result
val adapted = command.mapInput<NewArgs, OldArgs, Result, IdkError> { newArgs ->
    Ok(convertToOldArgs(newArgs))
}

// Simple transformation
val adapted = command.mapInputSimple { newArgs: NewArgs ->
    convertToOldArgs(newArgs)
}
```

### Output Transformation

```kotlin
// Full transformation
val transformed = command.mapOutput { result ->
    Ok(result.toDto())
}

// Simple transformation
val transformed = command.mapOutputSimple { result ->
    result.toDto()
}
```

### Error Transformation

```kotlin
val adapted = command.mapError { originalError ->
    MyCustomError.from(originalError)
}
```

### Filtering

```kotlin
val validated = command.filterOutput(
    predicate = { result -> result.isValid },
    onFailure = { result ->
        IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid: ${result.reason}")
    }
)
```

### Error Recovery

```kotlin
// Recover with alternative logic
val resilient = command.recover { error ->
    fetchFromCache() ?: Err(error)
}

// Use fallback value
val withDefault = command.withFallback(defaultValue)
```

### Side Effects

```kotlin
// On success
val logged = command.onSuccess { result ->
    log.info("Operation succeeded: $result")
}

// On failure
val withNotification = command.onFailure { error ->
    notificationService.notify("Operation failed: ${error.message}")
}
```

## Migration from Legacy Patterns

### andThen() → chain()

```kotlin
// OLD (deprecated, validates on INPUT instead of OUTPUT)
val combined = cmd1.andThen(cmd2)

// NEW (validates on OUTPUT correctly)
val combined = cmd1.chain("domain.capability.resource.action", cmd2)
```

### Manual Composition → PipeBuilder

```kotlin
// OLD
val result = parseCommand.execute(input)
if (result.isErr) return Err(result.error)
val validated = validateCommand.execute(result.value)
if (validated.isErr) return Err(validated.error)
return transformCommand.execute(validated.value)

// NEW
val pipeline = pipe("my.pipeline", parseCommand)
    .then(validateCommand)
    .then(transformCommand)
    .build()
val result = pipeline.execute(input)
```

## Best Practices

1. **Give meaningful IDs** to composed commands for debugging and authorization
2. **Use IdkError convenience functions** when possible (default error mapper)
3. **Prefer chain() over andThen()** for correct output validation
4. **Use PipeBuilder** for complex multi-step pipelines
5. **Implement compensation** for operations that modify state
6. **Keep transformations pure** where possible
7. **Use tap() for side effects** instead of modifying results
