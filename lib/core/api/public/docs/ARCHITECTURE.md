# Command Architecture

The IDK command system provides a unified abstraction for executing operations. Whether a command runs locally, over gRPC to another microservice, or behind an HTTP REST endpoint, the caller uses the same interface. This document explains how the pieces fit together.

---

## How a Request Flows Through the System

A typical request passes through several layers before reaching business logic:

```
External REST request (GET /keys/{id})
    │
    ▼
CommandBackedHttpAdapter (IDK)
    │  Matches request to an HttpEndpointCommand
    │  Strips base path, delegates to matching endpoint
    ▼
HttpEndpointCommand (IDK)
    │  Thin wrapper: extracts path/query params,
    │  delegates to a ServiceCommand
    ▼
ServiceCommand (IDK)
    │  The real business logic lives here
    │  CommandAdapter.execute() runs the lifecycle:
    │    1. Interceptors beforeExecute (ascending order)
    │    2. Enhanced extensions beforeExecute
    │    3. doExecute() — your implementation
    │    4. Extensions afterExecute (reverse order)
    │    5. Interceptors afterExecute (descending order, always runs)
    ▼
IdkResult<SuccessResult, IdkError>
```

For internal (service-to-service) calls, the path is different — EDK's transport layer resolves whether to execute a `ServiceCommand` locally or send it over gRPC to a remote service. The caller doesn't know or care which happens.

---

## The Command Hierarchy

Commands form a hierarchy from simple to full-featured:

```
BaseCommand<Arg, SuccessResult, ErrorResult>        ← minimal: execute() + supports()
    │
    ├── Command<Arg, SuccessResult, ErrorResult>     ← adds id, isEnabled, subsystem
    │       │
    │       ├── ServiceCommand<TInput, TOutput>      ← typed I/O, structured identity, transport-routable
    │       │
    │       ├── ChainCommand                         ← linked-list chaining
    │       ├── PipelineCommand / MultiService       ← aggregates multiple commands
    │       └── CompensatableCommand                 ← supports rollback for sagas
    │
    └── SimpleCommand<Arg, Result>                   ← lightweight, no session context
```

### BaseCommand

The foundation. A `fun interface` with two methods:

```kotlin
fun interface BaseCommand<Arg : Any, SuccessResult : Any, ErrorResult : IdkErrorType> {
    suspend fun supports(args: Any): Boolean = true
    suspend fun execute(args: Arg): IdkResult<SuccessResult, ErrorResult>
}
```

`supports()` intentionally takes `Any` — this allows heterogeneous dispatch where a pipeline of commands can be tested against an input without knowing its type at compile time.

### Command

Adds identity and lifecycle:

```kotlin
interface Command<Arg, SuccessResult, ErrorResult> : HasId, BaseCommand<...> {
    val id: String              // Hierarchical command ID (e.g., "kms.keys.get")
    val isEnabled: Boolean      // Feature gating
    val subsystem: EventSubsystem  // For event/audit categorization
}
```

### ServiceCommand

The primary pattern for domain operations. A `ServiceCommand` is a `Command` that also carries structured identity and type tokens for codec-based serialization:

```kotlin
interface ServiceCommand<TInput : Any, TOutput : Any> : Command<TInput, TOutput, IdkError> {
    val commandId: String          // "kms.keys.get"
    val inputTypeToken: TypeToken<TInput>
    val outputTypeToken: TypeToken<TOutput>

    // Structured identity derived from commandId:
    val module: String             // "kms"
    val service: String            // "keys"
    val command: String            // "get"
    val actionType: ActionType     // CREATE, READ, UPDATE, DELETE, LIST, EXECUTE
}
```

The type tokens enable transport layers to serialize/deserialize without runtime reflection, which is critical for Kotlin Multiplatform compatibility.

### SimpleCommand

For commands that don't need session context — pure transformations, utilities, or test helpers. Convert to a full `Command` with `asCommand()` when needed.

---

## Command IDs

Every command has a hierarchical ID following the format `{module}.{service}.{command}`:

| Command ID | Module | Service | Command |
|-----------|--------|---------|---------|
| `kms.keys.get` | kms | keys | get |
| `did.manager.resolve` | did | manager | resolve |
| `party.parties.create` | party | parties | create |
| `resource.booking.create` | resource | booking | create |

IDs are validated by regex: lowercase ASCII, dot-separated, exactly 3 segments, each starting with a letter. The `CommandId` value class provides parsing, validation, and glob-pattern matching (`*` for single segment, `**` for any depth, `{a,b}` for alternatives).

Command IDs serve multiple purposes: registry lookup, authorization pattern matching (see [AUTHORIZATION.md](./AUTHORIZATION.md)), transport routing, and event categorization.

---

## Command Adapters

You almost never implement `Command` directly. Instead, extend one of the adapters:

### CommandAdapter

The base adapter provides the execution lifecycle (extensions, interceptors, error mapping). You implement `doExecute()`:

```kotlin
class MyCommand : CommandAdapter<MyArgs, MyResult, IdkError>(
    id = "my.module.action"
) {
    override suspend fun doExecute(
        args: MyArgs,
        applyDuring: (MyArgs) -> MyArgs
    ): IdkResult<MyResult, IdkError> {
        val processedArgs = applyDuring(args)  // Apply extension transformations
        return Ok(result)
    }
}
```

**Important**: Override `doExecute()`, not `execute()`. The adapter's `execute()` orchestrates the full lifecycle around your `doExecute()`.

### ExecutionScopedCommandAdapter

Extends `CommandAdapter` with session context — access to logging, configuration, and the interceptor chain. Implements Amazon App Platform's `Scoped` interface for automatic session registration:

```kotlin
class MyCommand @Inject constructor(
    execution: SessionExecution
) : ExecutionScopedCommandAdapter<MyArgs, MyResult, IdkError>(
    id = "my.module.action",
    execution = execution
) {
    override suspend fun doExecute(
        args: MyArgs,
        applyDuring: (MyArgs) -> MyArgs
    ): IdkResult<MyResult, IdkError> {
        log.info("Executing with tenant: ${conf.tenant}")
        // ...
    }
}
```

`SessionExecution` bundles the session context, log service, hierarchical configuration, and interceptor chain.

### TypedServiceCommandAdapter

The standard adapter for `ServiceCommand` implementations. Adds type tokens on top of `ExecutionScopedCommandAdapter`:

```kotlin
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, boundType = GetKeyServiceCommand::class)
class GetKeyServiceCommandImpl(
    execution: SessionExecution,
    private val keyStore: KeyStore
) : TypedServiceCommandAdapter<GetKeyInput, KeyInfo>(
    commandId = "kms.keys.get",
    execution = execution,
    inputTypeToken = typeToken<GetKeyInput>(),
    outputTypeToken = typeToken<KeyInfo>()
), GetKeyServiceCommand {
    override suspend fun doExecute(
        args: GetKeyInput,
        applyDuring: (GetKeyInput) -> GetKeyInput
    ): IdkResult<KeyInfo, IdkError> {
        val input = applyDuring(args)
        return keyStore.getKey(input.aliasOrKid)
            ?.let { Ok(it) }
            ?: Err(IdkError.NOT_FOUND_ERROR(message = "Key not found"))
    }
}
```

---

## HTTP Endpoint Commands (External REST API)

The IDK provides a two-layer pattern for exposing commands as REST endpoints:

### PublicApiCommand

A marker interface on `ServiceCommand` that declares the public REST binding:

```kotlin
interface GetKeyServiceCommand :
    ServiceCommand<GetKeyInput, KeyInfo>,
    PublicApiCommand {
    override val httpMethod: String get() = "GET"
    override val httpPath: String get() = "/keys/{aliasOrKid}"
}
```

This declares *what* the public API looks like. The actual HTTP handling is done by `CommandBackedHttpAdapter`.

### CommandBackedHttpAdapter

An HTTP adapter that aggregates `HttpEndpointCommand` instances. Each endpoint command is a thin wrapper that extracts HTTP-specific data (path params, query params, headers) and delegates to a service command:

```kotlin
class KmsHttpAdapter(
    execution: SessionExecution,
    getKeyEndpoint: GetKeyEndpointCommand,
    listKeysEndpoint: ListKeysEndpointCommand,
) : CommandBackedHttpAdapter(
    id = "kms-adapter",
    execution = execution,
    mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/kms")
) {
    override val endpointCommands: List<HttpEndpointCommand> by lazy {
        listOf(getKeyEndpoint, listKeysEndpoint)
    }
}
```

The adapter handles request routing (matching HTTP method + path pattern to the right endpoint), base-path stripping, and error-to-HTTP-status mapping. Individual `HttpEndpointCommand` implementations should be simple — extract parameters from the request, call the service command, format the response.

### Design Intent

HTTP endpoint commands are *presentation layer* adapters. They should not contain business logic. The pattern is:

```
REST endpoint (HttpEndpointCommand) → thin adapter → ServiceCommand (business logic)
```

This separation means the same `ServiceCommand` can be invoked via REST, gRPC, or in-process without any changes to the business logic.

---

## Transport Routing (EDK/VDX)

EDK and VDX extend the IDK command model with transparent cross-service routing. The key insight: a `ServiceCommand` interface is the same whether the implementation runs locally or on a remote server.

### How Routing Works

EDK determines at runtime whether a command should execute locally or be forwarded to another service:

```
Caller invokes ServiceCommand.execute(input)
    │
    ├─ ExecutionTargetResolver checks config
    │   → LOCAL:  LocalServiceCommandTransport → in-process execution
    │   → SERVER: GrpcServiceCommandTransport  → gRPC to remote service
    │
    ▼
Same IdkResult<Output, IdkError> either way
```

Configuration drives the routing. For example, `service-data` routes KMS commands to `service-crypto`:

```
SPHEREON_TRANSPORT_ROUTING_MODULES_KMS_TARGET=SERVER
SPHEREON_TRANSPORT_ROUTING_MODULES_KMS_TRANSPORT=GRPC
SPHEREON_TRANSPORT_ROUTING_MODULES_KMS_ENDPOINT=service-crypto:9090
```

### Convention-Based Binding

IDK command interfaces carry no transport metadata. EDK derives internal transport bindings automatically from the command ID using `TransportConventionResolver`:

| Command ID | HTTP RPC | gRPC Service | gRPC Method |
|-----------|----------|-------------|-------------|
| `kms.keys.get` | `POST /rpc/kms/keys/get` | `kms.KeysService` | `Get` |
| `party.manager.create` | `POST /rpc/party/manager/create` | `party.ManagerService` | `Create` |

This convention means adding a new command automatically gets transport bindings without any configuration.

### The `-impl` and `-remote` Package Convention

Domain modules follow a two-package pattern:

- **`-impl`** packages contain the actual business logic (`GetKeyServiceCommandImpl`)
- **`-remote`** packages contain KSP-generated routing adapters (`GetKeyServiceCommandRouted`) that delegate to the transport layer

Both are bound via DI. A `RoutingCommandDelegator` selects the right implementation at runtime based on the execution target config.

### Dual Transport Server (VDX)

VDX services expose both REST and gRPC via `DualTransportServer`:

```
DualTransportServer
    ├── Ktor REST server (port 8080)     → external REST API (PublicApiCommand routes)
    │                                     → internal HTTP RPC (/rpc/... routes)
    └── gRPC server (port 9090)          → internal service-to-service calls
```

Both transports feed into a unified `BinaryCommandAdapter` that handles authentication, command lookup, policy enforcement, codec negotiation (JSON/CBOR/Protobuf), execution, and audit logging.

---

## Command Execution Lifecycle

When `CommandAdapter.execute()` is called, the following happens:

```
execute(args)
    │
    ├─ Check isEnabled (return error if disabled)
    ├─ Check supports(args) (return error if unsupported)
    │
    ├─ PHASE 1: Interceptor beforeExecute (ascending order)
    │   All interceptors run even if one denies — audit interceptors
    │   can always observe what happened
    │
    ├─ If any interceptor denied → return authorization error
    │
    ├─ PHASE 2: Enhanced extension beforeExecute
    │   Can Continue (modify args), Skip, or ShortCircuit (return cached result)
    │
    ├─ Regular extension beforeExecute
    │
    ├─ doExecute(args, applyDuring)  ← YOUR CODE
    │
    ├─ Regular extension afterExecute
    ├─ Enhanced extension afterExecute (reverse order)
    │
    └─ PHASE 3: Interceptor afterExecute (descending order, ALWAYS runs)
        Even on exceptions or denials
```

### Interceptors vs Extensions

These serve different roles:

**Interceptors** (`CommandLifecycleInterceptor`) are cross-cutting concerns that wrap the *entire* execution including extensions. IDK defines the interface and provides an empty chain; EDK/VDX replace it via DI with concrete interceptors for policy enforcement, telemetry, and audit logging. All interceptors always run (even after a denial), and interceptor failures never fail command execution.

**Extensions** (`IEnhancedCommandExecutionExtension`) are per-command hooks that participate in the execution flow. They can modify arguments, short-circuit execution (for caching or authorization), and transform results. They are configured per command instance.

### BeforeExecuteResult

Enhanced extensions return a sealed class that controls execution flow:

- **Continue(args)** — proceed with (possibly modified) arguments
- **Skip** — skip execution entirely (command's `doExecute` is not called)
- **ShortCircuit(result)** — return a specific result without executing (useful for caching)

---

## Command Composition

### Chaining

Chain two commands where the output of the first becomes the input of the second:

```kotlin
val pipeline = parseCommand.chain("pipeline.parse-and-validate", validateCommand)
```

Or chain multiple commands:

```kotlin
val pipeline = chainAll<RawInput, FinalOutput, IdkError>(
    "pipeline.full",
    listOf(parseCommand, validateCommand, transformCommand),
    IdkErrorCommandErrorMapper
)
```

### Functional Composition

Transform inputs, outputs, and errors without modifying the underlying command:

```kotlin
val adapted = originalCommand
    .mapInput<ExternalInput, InternalInput, Output, IdkError> { ext -> Ok(ext.toInternal()) }
    .mapOutput { result -> Ok(result.toExternalFormat()) }
    .recover { error -> Ok(defaultValue) }
```

### Saga (Compensatable Commands)

For operations that must all succeed or all be rolled back:

```kotlin
val saga = sagaBuilder<CreateOrderArgs, Order, IdkError>("order.saga.create")
    .step(createOrderCommand)
    .step(reserveInventoryCommand)
    .step(chargePaymentCommand)
    .build()
```

If `chargePaymentCommand` fails, `reserveInventoryCommand.compensate()` and `createOrderCommand.compensate()` are called in reverse order.

### DSL

Create commands declaratively:

```kotlin
val cmd = command<UserId, User>("data.user.get") {
    subsystem(EventSubsystems.CUSTOM)
    supports { args -> args is UserId }
    execute { userId ->
        userRepository.findById(userId)
            ?.let { Ok(it) }
            ?: Err(IdkError.NOT_FOUND_ERROR(message = "User not found"))
    }
}
```

---

## Service Command Groups

Related commands are grouped into `ServiceCommandGroup` descriptions for discoverability:

```kotlin
ServiceCommandGroupDescription(
    groupId = "kms.keys",
    module = "kms",
    service = "keys",
    displayName = "KMS Key Management",
    commandIds = listOf("kms.keys.get", "kms.keys.list", "kms.keys.generate", "kms.keys.delete")
)
```

Groups are collected into a `ServiceCommandGroupCatalog` via DI multibinding. Transport servers, admin endpoints, and health checks use the catalog to discover available commands.

### Service Facades

For a cleaner consumer API, related commands can be aggregated behind a `ServiceFacade`:

```kotlin
interface KmsServiceFacade : ServiceFacade {
    override val serviceId: String get() = "kms"
    suspend fun getKey(aliasOrKid: String): IdkResult<KeyInfo, IdkError>
    suspend fun listKeys(limit: Int = 100): IdkResult<List<KeyInfo>, IdkError>
}
```

The facade implementation simply delegates to the underlying `ServiceCommand` instances.

---

## Error Handling

Commands return `IdkResult<SuccessResult, ErrorResult>` — never throw exceptions for business errors. The `CommandErrorMapper` interface provides consistent error creation:

```kotlin
interface CommandErrorMapper<E : IdkErrorType> {
    fun unsupportedArg(command: Any, arg: Any): E
    fun commandDisabled(commandId: String): E
    fun notAuthorized(commandId: CommandId, reason: String): E
    fun commandNotFound(commandId: String): E
    // ...
}
```

`IdkErrorCommandErrorMapper` is the default implementation that maps to standard `IdkError` codes.

---

## EDK/VDX: What They Add

The IDK provides the command abstractions. EDK and VDX add the operational infrastructure:

**EDK** provides:
- `TransportConventionResolver` — derives HTTP RPC and gRPC bindings from command IDs
- `ServiceCommandTransport` — unified transport interface with local, HTTP, and gRPC implementations
- `ExecutionTargetResolver` — config-driven LOCAL/SERVER routing
- KSP-generated routed command adapters (the `-remote` packages)
- Telemetry interceptor (creates spans per command, propagates W3C trace context)
- Audit interceptor (records command execution to `AuditLogService`)
- Policy interceptor (enforces authorization rules before execution)

**VDX** provides:
- `DualTransportServer` — starts REST + gRPC for each microservice
- `BinaryCommandAdapter` — unified entry point for both transports
- Ktor and gRPC transport server implementations
- `CommandIdResolver` — maps incoming HTTP method + path to command IDs
- SIEM export for audit events

---

## Best Practices

1. **Implement `doExecute()`**, not `execute()`, in adapters
2. **Call `applyDuring(args)`** to honor extension argument transformations
3. **Return `IdkResult` errors** instead of throwing exceptions
4. **Never throw in `supports()`** — return `false` for unsupported inputs
5. **Use `ServiceCommand`** for domain operations that may need transport routing
6. **Keep HTTP endpoint commands thin** — extract params, delegate to service commands
7. **Use hierarchical command IDs** (`module.service.command`) for consistent authorization and routing
8. **Inject dependencies** via constructor, never instantiate commands directly
