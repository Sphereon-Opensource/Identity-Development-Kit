# Audit Logging and Events

The IDK includes a structured event system that serves as the foundation for audit logging. Every command execution can automatically produce lifecycle events (started, completed, failed) that flow through a central hub for real-time subscriptions, get persisted to a store for later querying, and can optionally be signed or encrypted for compliance. EDK and VDX build on this with OpenTelemetry tracing, structured audit records, and SIEM export.

This document focuses on the IDK layer and briefly describes what EDK/VDX add.

---

## How Events Flow Through the System

When a command executes, the event system works like this:

```
Command executes
    │
    ▼
EventCommandExtension (automatic, per-command)
    │  Creates Event with type, subsystem, category, payload
    │  Attaches EventContext (session, tenant, principal)
    ▼
EventService.emit(event)
    │  Optionally signs (JWS) and encrypts (JWE) via KMS
    ├──► EventStore.store(event)    ← persisted for querying
    └──► EventHub.publish(event)    ← broadcast to subscribers
              │
              ▼
         Subscribers (filtered)
         ├─ Alert on errors
         ├─ Forward to external systems
         └─ Aggregate metrics
```

You can also emit events manually from within a command for domain-specific actions that go beyond the automatic lifecycle events.

---

## The Event Model

An `Event` is an immutable record of something that happened. It carries:

- **What happened**: `type` (e.g., `command.completed`, `session.created`)
- **Where it came from**: `origin` (the command ID), `subsystem` (e.g., `crypto`, `kms`)
- **How to categorize it**: `category` (e.g., `lifecycle`, `error`, `security`)
- **Who was involved**: `context` with sessionId, tenantId, principalId, correlationId
- **Details**: `payload` as a `JsonObject` with event-specific data
- **When**: `timestamp` as `Instant`

All three classification dimensions -- type, subsystem, category -- are extensible `@JvmInline value class` types. The IDK provides standard values, and you can define your own:

```kotlin
// IDK provides these
EventTypes.COMMAND_STARTED / COMMAND_COMPLETED / COMMAND_FAILED
EventSubsystems.CRYPTO / KMS / OID4VP / SESSION / ...
EventCategories.LIFECYCLE / OPERATION / ERROR / SECURITY / VERBOSE

// You can extend them
val KEY_ROTATED = EventType("kms.key.rotated")
val BOOKING = EventSubsystem("booking")
val COMPLIANCE = EventCategory("compliance")
```

All three support glob-pattern matching (`*` for single segment, `**` for any depth), which is used throughout filtering and configuration.

### EventContext

Every event carries an `EventContext` that captures who was involved:

```kotlin
data class EventContext(
    val sessionId: String?,
    val tenantId: String?,
    val principalId: String?,
    val correlationId: String?
)
```

When you emit through a `SessionEventService`, the context is automatically populated from the current session. Anonymous identity constants are normalized to `null`, so `isAnonymous()` reliably indicates a truly anonymous context.

---

## Automatic Command Lifecycle Events

The simplest way to get audit-style logging is to register `EventCommandExtension` with your commands. It hooks into the command execution lifecycle as an `IEnhancedCommandExecutionExtension` and automatically emits:

| Phase | Event Type | Category | When |
|-------|-----------|----------|------|
| Before execution | `COMMAND_STARTED` | `LIFECYCLE` | If `emitOnStart = true` |
| After success | `COMMAND_COMPLETED` | `LIFECYCLE` | If `emitOnSuccess = true` |
| After failure | `COMMAND_FAILED` | `ERROR` | If `emitOnFailure = true` |

The completion events include duration in milliseconds and (on failure) the error type. Events are emitted fire-and-forget in a background coroutine so they never block command execution.

```kotlin
class MyCommandImpl(
    execution: SessionExecution,
    eventExtension: EventCommandExtension<MyArgs, MyResult, IdkError>
) : ExecutionScopedCommandAdapter<MyArgs, MyResult, IdkError>(
    id = "my.subsystem.operation",
    execution = execution,
    executionExtensions = arrayOf(eventExtension)
)
```

### Controlling What Gets Emitted

`CommandEventConfig` gives you fine-grained control over which commands emit events, using glob patterns against command IDs:

```kotlin
val config = CommandEventConfig(
    includePatterns = listOf("party.**", "resource.**"),
    excludePatterns = listOf("**.health", "**.metrics"),
    commandOverrides = mapOf("party.list" to false),  // Never for this one
    emitOnStart = false  // Only completion events
)
```

Evaluation order: global `enabled` flag -> per-command overrides -> exclude patterns -> include patterns.

Three presets cover common cases:
- `CommandEventConfig.DEFAULT` -- emit everything for all commands
- `CommandEventConfig.DISABLED` -- emit nothing
- `CommandEventConfig.FAILURES_ONLY` -- only failures

### Preventing Infinite Loops

Commands that are part of the event system itself (storing events, querying events) must not emit events about themselves, or you get an infinite loop. Mark these with the `SilentCommand` marker interface:

```kotlin
interface StoreEventCommand : SilentCommand<StoreEventArgs, StoreEventResult, IdkError>
```

For commands that should just be quiet (health checks, metrics), prefer `CommandEventConfig.excludePatterns` instead -- it is configurable at runtime rather than hard-coded.

---

## Emitting Custom Events

Beyond automatic lifecycle events, you can emit domain-specific events from within a command by injecting `SessionEventService`:

```kotlin
@Inject
class IssueCredentialCommand(
    execution: SessionExecution,
    private val eventService: SessionEventService
) : ExecutionScopedCommandAdapter<IssueArgs, Credential, IdkError>(...) {

    override suspend fun doExecute(arg: IssueArgs): IdkResult<Credential, IdkError> {
        val credential = issueCredential(arg)

        eventService.emit(
            eventService.eventBuilder()
                .type(EventType("credential.issued"))
                .origin(id)
                .subsystem(EventSubsystems.OID4VCI)
                .category(EventCategories.OPERATION)
                .payload(buildJsonObject {
                    put("credentialType", arg.type)
                    put("holderId", arg.holderId)
                })
                .build()
        )

        return Ok(credential)
    }
}
```

The event service hierarchy follows the same App -> User -> Session scope pattern as configuration. `SessionEventService` is the most common choice because it has the full context (session, tenant, principal). `AppEventService` and `UserEventService` exist for cases where you don't have a session yet.

---

## Subscribing to Events

The `EventHub` is the central broadcast point. All events from all scopes flow through it as a Kotlin `SharedFlow`. You can subscribe with filters to react in real time:

```kotlin
val job = eventHub.subscribe(coroutineScope) {
    filter {
        subsystem(EventSubsystems.CRYPTO)
        category(EventCategories.ERROR)
    }
    onEvent { event ->
        alertService.notify("Crypto error in ${event.origin}")
    }
}

// Cancel when no longer needed
job.cancel()
```

You can also use the Flow-based API for integration with other Flow operators:

```kotlin
eventHub.eventsByTypePattern("command.*")
    .filter { it.category == EventCategories.ERROR }
    .collect { event -> logError(event) }
```

### Filtering

`EventFilter` combines multiple criteria with AND logic. Empty criteria match all events for that dimension:

```kotlin
val filter = eventFilter {
    typePatterns("command.*")                     // Any command event
    subsystems(listOf(EventSubsystems.KMS))       // From KMS only
    category(EventCategories.SECURITY)             // Security category
    originPatterns("did.manager.**")              // From DID manager commands
}
```

Filters are composable: `filterA.and(filterB)`. Two predefined filters exist: `EventFilter.ALL` and `EventFilter.NONE`.

---

## Persisting and Querying Events

The `EventStore` persists events for later retrieval. IDK provides `RingBufferEventStore` -- an in-memory ring buffer with configurable capacity (default 10,000 events). EDK provides database-backed implementations.

```kotlin
// Query recent failures
val failures = eventStore.query(
    filter = eventFilter { type(EventTypes.COMMAND_FAILED) },
    limit = 50
).getOrThrow()

// Retention cleanup
eventStore.deleteOlderThan(olderThanMillis = 7 * 24 * 60 * 60 * 1000L)
```

Events are stored automatically by `EventService.emit()` -- you don't need to call `store()` manually unless you are building a custom pipeline.

---

## Event Signing and Encryption

For compliance-sensitive scenarios, events can be cryptographically signed and/or encrypted using the Key Management System (KMS).

**Signing** (JWS) provides authenticity, integrity, and non-repudiation. A signed event carries an `EventSignature` with the key alias, algorithm, and JWS compact serialization.

**Encryption** (JWE) provides confidentiality. You can choose which parts to encrypt: the payload, the context (session/tenant/principal identifiers), or both.

```kotlin
eventService.emit(
    event,
    sign = true,
    encrypt = true,
    encryptParts = setOf(EncryptedPart.PAYLOAD, EncryptedPart.CONTEXT)
)
```

IDK provides no-op implementations (`NoOpEventSigningService`, `NoOpEventEncryptionService`) that pass events through unchanged. Real implementations are provided by EDK when a KMS is configured.

---

## Command Lifecycle Interceptors

In addition to `EventCommandExtension` (which works at the enhanced-extension level within a command), the IDK defines `CommandLifecycleInterceptor` -- a lower-level cross-cutting hook that wraps the entire command execution including the extensions. EDK/VDX use this for telemetry, policy enforcement, and audit logging.

The key design properties are:
- All interceptors run even after a denial -- audit interceptors can always observe what happened
- `afterExecute` always runs, even on exceptions
- Interceptors are ordered: ascending for `beforeExecute`, descending for `afterExecute`

```
CommandAdapter.execute(args)
    │
    ├─ beforeExecute (ascending order)
    │   ├─ TelemetryInterceptor (10)   → Creates tracing span
    │   ├─ PolicyInterceptor (100)     → Continue or Deny
    │   └─ AuditInterceptor (200)      → Emits STARTED audit event
    │   (all run even if one denies)
    │
    ├─ Command execution (skipped if denied)
    │   └─ doExecute() with extensions
    │
    └─ afterExecute (descending order, always runs)
        ├─ AuditInterceptor (200)      → Emits SUCCEEDED / FAILED / POLICY_DENIED
        ├─ PolicyInterceptor (100)     → (no-op)
        └─ TelemetryInterceptor (10)   → Ends span, records metrics
```

IDK provides an empty interceptor chain by default. Downstream layers replace it via DI to install their concrete interceptors. This means in pure IDK usage you get event system support (via `EventCommandExtension`) but no interceptor-level audit or telemetry -- those come from EDK/VDX.

---

## W3C Trace Context

IDK includes a `TraceContext` data class for distributed trace propagation following the W3C Trace Context specification:

```kotlin
data class TraceContext(
    val traceId: String,            // 32-char hex
    val spanId: String,             // 16-char hex
    val parentSpanId: String?,
    val traceFlags: Int = 0         // 0x01 = sampled
)
```

It supports serialization to/from the `traceparent` header format (`00-{traceId}-{spanId}-{flags}`). This is the IDK-level primitive that the EDK telemetry layer builds on for OpenTelemetry integration.

---

## Testing

**Capture events** by subscribing to the hub:

```kotlin
val captured = mutableListOf<Event>()
val job = eventHub.subscribe(testScope, EventFilter.ALL) { captured.add(it) }

// ... execute command ...

assertEquals(EventTypes.COMMAND_COMPLETED, captured.last().type)
job.cancel()
```

**Disable events** when they are not relevant to a test:

```kotlin
val extension = EventCommandExtension.create<MyArgs, MyResult, IdkError>(
    eventServiceProvider = { testEventService },
    config = CommandEventConfig.DISABLED
)
```

**Query stored events** using the in-memory ring buffer:

```kotlin
val store = RingBufferEventStore(EventStoreConfig(capacity = 100))
// ... emit events ...
val errors = store.query(eventFilter { category(EventCategories.ERROR) }).getOrThrow()
```

---

## EDK/VDX: Telemetry and Audit

The EDK and VDX layers extend the IDK event system with observability and compliance features.

### Distributed Tracing (EDK)

EDK defines tracing abstractions (`TracerProvider`, `Tracer`, `Span`, `MetricsCollector`) with no-op defaults. When the `telemetry-otel` module is on the classpath, OpenTelemetry SDK implementations replace them via DI. This gives you distributed tracing without any IDK code depending on OpenTelemetry directly.

EDK also provides `LogCorrelation` which extracts `traceId` and `spanId` from the current coroutine context and merges them into log metadata, enabling log aggregation systems to link log entries to their corresponding traces.

Configuration uses standard OpenTelemetry environment variables (`OTEL_SERVICE_NAME`, `OTEL_TRACES_EXPORTER`, `OTEL_EXPORTER_OTLP_ENDPOINT`, etc.).

### Transport-Level Tracing (VDX)

VDX provides Ktor and gRPC interceptors that extract/inject W3C `traceparent` headers at the transport boundary. This creates parent spans for inbound requests, and propagates trace context to outbound gRPC calls for cross-service tracing:

```
HTTP request → KtorTelemetryPlugin (transport span)
                 └─ TelemetryInterceptor (command span)
                      └─ GrpcClientTelemetryInterceptor (outbound span)
                           └─ Remote service (continues the trace)
```

### Structured Audit Logging (EDK)

EDK provides a dedicated `AuditLogService` pipeline that processes audit events through three stages: **enrich** (fill tenant/actor/correlation from session context), **redact** (mask sensitive keys like passwords and tokens), and **persist** (write to immutable store). The EDK `AuditEvent` is richer than the IDK `Event` -- it includes fields for `traceId`, `spanId`, `errorCode`, `transportType`, and result types like `POLICY_DENIED` and `AUTH_FAILURE` that map to interceptor verdicts.

### SIEM Export (VDX)

VDX includes a `SiemExporter` that forwards audit events to external security information systems via syslog+TLS or HTTPS webhook. It supports multiple output formats (JSON, CEF, OCSF) through `AuditFormatter` implementations, and includes async retry with dead-letter logging. It never blocks command execution.
