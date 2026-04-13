# Command Authorization

This document describes the authorization system across the IDK, EDK, and VDX layers.

## Command ID Format

Command IDs follow a **three-segment** hierarchical format:

```
<module>.<service>.<command>
```

| Segment | Description | Examples |
|---------|-------------|----------|
| module | Top-level domain module | `kms`, `did`, `party`, `crypto`, `eidas`, `theme` |
| service | Service within the module | `keys`, `manager`, `parties`, `encryption`, `signatures` |
| command | Operation to perform | `create`, `get`, `list`, `resolve`, `encrypt`, `sign` |

### Validation Rules

- Exactly **3 segments** separated by dots
- Each segment starts with a lowercase letter
- Segments contain only lowercase ASCII letters, digits, and hyphens
- No environment or tenant-specific data in IDs

Regex: `^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*){2}$`

### Examples

```
kms.keys.get             # KMS module, keys service, get command
kms.encryption.encrypt   # KMS module, encryption service, encrypt command
kms.encryption.decrypt   # KMS module, encryption service, decrypt command
did.manager.resolve      # DID module, manager service, resolve command
party.parties.create     # Party module, parties service, create command
party.parties.list       # Party module, parties service, list command
crypto.jws.compact       # Crypto module, JWS service, compact command
crypto.jwe.decrypt       # Crypto module, JWE service, decrypt command
eidas.signatures.sign    # eIDAS module, signatures service, sign command
eidas.configs.create     # eIDAS module, configs service, create command
claims.config.create     # Claims module, config service, create command
theme.resolution.resolve # Theme module, resolution service, resolve command
theme.definitions.get    # Theme module, definitions service, get command
health.system.detailed   # Health module, system service, detailed command
```

### CommandId Value Class

```kotlin
val id = CommandId("kms.keys.get")

// Access segments
id.module   // "kms"
id.service  // "keys"
id.command  // "get"

// Factory methods
val id = CommandId.of("kms", "keys", "get")
val id = CommandId.tryParse("kms.keys.get") // returns null if invalid

// Pattern matching
id.matches("kms.**")               // true
id.matches("kms.keys.*")           // true
id.matches("*.keys.get")           // true
id.matches("kms.{keys,encryption}.get") // true
```

### Parsing Command IDs

For safe parsing of command ID strings:

```kotlin
val id: CommandId? = CommandId.tryParse("kms.keys.get")
// id.module  = "kms"
// id.service = "keys"
// id.command = "get"

// Or with IdkResult for error handling:
val result: IdkResult<CommandId, IdkError> = CommandId.tryParseResult("kms.keys.get")
```

## Pattern Matching

The authorization system supports three wildcard types for matching command IDs:

| Pattern | Description | Example |
|---------|-------------|---------|
| `*` | Match any single segment | `kms.*.get` |
| `**` | Match any remaining segments | `kms.**` |
| `{a,b}` | Match one of multiple values | `kms.{keys,encryption}.get` |

### Pattern Examples

```kotlin
// Single wildcard — matches any single segment
matchesAdvancedPattern("kms.*.get", "kms.keys.get")           // true
matchesAdvancedPattern("kms.*.get", "kms.encryption.get")     // true
matchesAdvancedPattern("kms.*.get", "did.keys.get")           // false

// Double wildcard — matches any remaining segments
matchesAdvancedPattern("kms.**", "kms.keys.get")              // true
matchesAdvancedPattern("kms.**", "kms.encryption.decrypt")    // true
matchesAdvancedPattern("kms.**", "did.manager.resolve")       // false

// Alternatives — matches one of multiple values
matchesAdvancedPattern("kms.{keys,encryption}.get", "kms.keys.get")        // true
matchesAdvancedPattern("kms.{keys,encryption}.get", "kms.encryption.get")  // true
matchesAdvancedPattern("kms.{keys,encryption}.get", "kms.signing.get")     // false

// Combined patterns
matchesAdvancedPattern("*.keys.*", "kms.keys.get")             // true
matchesAdvancedPattern("*.keys.*", "crypto.keys.generate")     // true
matchesAdvancedPattern("{kms,crypto}.*.get", "kms.keys.get")   // true
```

## Architecture Overview

Authorization is layered across three levels:

```
┌─────────────────────────────────────────────────────────────┐
│ VDX Transport Layer                                         │
│  PolicyEnforcer → PolicyEngine (OPA/Cedarling/AuthZEN)      │
│  Enforced in BinaryCommandAdapter before command execution  │
├─────────────────────────────────────────────────────────────┤
│ EDK Authorization Layer                                     │
│  AuthZEN PDP integration, Cedarling, OPA adapters           │
│  PolicyEngine interface, tenant-aware engine resolution     │
│  Dual-principal evaluation, step-up auth (RFC 9470)         │
├─────────────────────────────────────────────────────────────┤
│ IDK Core Layer                                              │
│  CommandAuthorizer interface, pattern matching               │
│  PolicyDecisionProvider interface, PolicyContext             │
│  Command extension hooks (beforeExecute)                    │
└─────────────────────────────────────────────────────────────┘
```

## IDK Core Authorizers

### CommandAuthorizer Interface

```kotlin
interface CommandAuthorizer {
    suspend fun isAuthorized(
        commandId: CommandId,
        sessionContext: SessionContext
    ): IdkResult<Unit, IdkError>
}
```

Returns:
- `Ok(Unit)` — command is authorized
- `Err(IdkError)` — command is not authorized

### PermissiveAuthorizer

Allows all commands. Default in IDK for development:

```kotlin
object PermissiveAuthorizer : CommandAuthorizer {
    override suspend fun isAuthorized(...) = Ok(Unit)
}
```

### PatternCommandAuthorizer

Allowlist-based authorization using patterns:

```kotlin
val authorizer = PatternCommandAuthorizer.fromPatterns(
    "kms.**",                              // All KMS commands
    "did.manager.resolve",                 // Specific DID resolution
    "party.{parties,contacts}.get"         // Get party or contact
)

authorizer.isAuthorized(CommandId("kms.keys.get"), ctx)  // Ok(Unit)
authorizer.isAuthorized(CommandId("eidas.signatures.sign"), ctx)  // Err(...)
```

Empty pattern set means all commands are allowed:

```kotlin
val permissive = PatternCommandAuthorizer(emptySet())
```

### DenyPatternAuthorizer

Denylist-based authorization:

```kotlin
val authorizer = DenyPatternAuthorizer.fromPatterns(
    "eidas.**",                    // Block all eIDAS commands
    "*.*.delete"                   // Block all delete operations
)
```

### CompositeAuthorizer

Combines multiple authorizers (all must pass):

```kotlin
val authorizer = CompositeAuthorizer.of(
    PatternCommandAuthorizer.fromPatterns("kms.**", "did.**"),
    DenyPatternAuthorizer.fromPatterns("*.*.delete"),
    customRoleAuthorizer
)
```

## IDK Policy Decision Provider

For external policy engines (OPA, Cedar), IDK defines a bridge interface:

### PolicyDecisionProvider

```kotlin
interface PolicyDecisionProvider {
    suspend fun isAllowed(context: PolicyContext): IdkResult<Boolean, IdkError>
}

data class PolicyContext(
    val actorId: String,
    val subjectId: String? = null,
    val resourceId: String? = null,
    val commandId: String,
    val correlationId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
```

Built-in implementations:
- `PermissivePolicyProvider` — always allows (IDK default)
- `DenyAllPolicyProvider` — always denies (testing / fail-closed)

### PolicyAuthorizer Bridge

Adapts `PolicyDecisionProvider` to `CommandAuthorizer`:

```kotlin
val authorizer = PolicyAuthorizer(
    policy = opaPolicyProvider,
    contextBuilder = { cmdId, session ->
        PolicyContext(
            actorId = session.sessionId,
            commandId = cmdId.value,
            resourceId = extractResourceId(session)
        )
    }
)
```

The `toPolicyResource()` helper converts a `PolicyContext` to a flat map with parsed command ID parts (`module`, `service`, `command`) suitable for policy engine input.

## EDK Policy Engine Abstraction

The EDK provides a richer policy engine interface that maps to multiple policy languages.

### PolicyEngine Interface

```kotlin
interface PolicyEngine {
    val id: String
    val engineType: PolicyEngineType  // CEDAR, OPA, AUTHZEN, CUSTOM
    suspend fun isHealthy(): Boolean
    suspend fun evaluate(request: PolicyRequest): IdkResult<PolicyDecision, IdkError>
    suspend fun evaluateBatch(
        requests: List<PolicyRequest>,
        combineWith: BatchCombineMode = BatchCombineMode.ALL_PERMIT
    ): IdkResult<PolicyDecision, IdkError>
}
```

### PolicyRequest Model

Uses a principal-action-resource-context model that maps to various policy languages:

```kotlin
data class PolicyRequest(
    val principal: PolicyPrincipal,  // Entity making the request (User, Workload, Role)
    val action: PolicyAction,        // Operation being requested (command ID)
    val resource: PolicyResource,    // Target of the request
    val context: PolicyContext       // Tenant, session, tokens, attributes
)
```

### PolicyDecision

```kotlin
data class PolicyDecision(
    val decision: Decision,                           // PERMIT, DENY, STEP_UP_REQUIRED
    val reasons: List<String> = emptyList(),
    val diagnostics: Map<String, JsonElement> = emptyMap()
)
```

The `Decision` enum includes:
- `PERMIT` — access is permitted
- `DENY` — access is denied
- `STEP_UP_REQUIRED` — access denied due to insufficient authentication assurance (RFC 9470)

### Batch Evaluation

Multiple requests can be evaluated together with combining strategies:
- `BatchCombineMode.ALL_PERMIT` — all must permit (AND logic)
- `BatchCombineMode.ANY_PERMIT` — at least one must permit (OR logic)

## EDK AuthZEN Integration

The EDK implements the [OpenID AuthZEN](https://openid.net/specs/authorization-interop/) specification for interoperability with external Policy Decision Points (PDPs).

### Supported PDP Types

| PDP | Description | Default Evaluation Path |
|-----|-------------|------------------------|
| **Cedarling** | Cedar policy engine via Janssen sidecar | `/cedarling/evaluation` |
| **OPA** | Open Policy Agent with Rego policies | `/v1/data/sphereon/authz` |
| **Generic** | Standards-compliant AuthZEN endpoint | `/access/v1/evaluation` |

### AuthZEN Request/Response

The AuthZEN integration maps the internal `PolicyRequest` to the AuthZEN 4-tuple:

| AuthZEN Concept | Source |
|-----------------|--------|
| **Subject** | User/Workload/Role extracted from JWT |
| **Action** | Command ID with module/service/command attributes |
| **Resource** | Command type + optional resource ID |
| **Context** | Tenant, session, tokens, transport scope |

### AuthZenConfig

```kotlin
data class AuthZenConfig(
    val enabled: Boolean = false,
    val pdp: AuthZenPdpConfig? = null,
    val fallbackPolicy: FallbackPolicy = FallbackPolicy.DENY,
    val cacheEnabled: Boolean = true,
    val cacheTtlSeconds: Long = 300,
    val excludePatterns: List<String> = listOf("health.**", "discovery.**", "actuator.**"),
    val includePatterns: List<String> = listOf("**"),
    val resilience: ResilienceConfig = ResilienceConfig(),
    val dualPrincipalMode: DualPrincipalMode = DualPrincipalMode.PRIMARY_ONLY,
    val enforcementMode: EnforcementMode = EnforcementMode.ENFORCED
)
```

### Enforcement Modes

| Mode | Behavior |
|------|----------|
| `DISABLED` | No policy evaluation performed |
| `LOG_ONLY` | Evaluate policies but always permit; log decisions |
| `ENFORCED` | Full enforcement — deny decisions block commands |

### Fallback Policies

| Policy | Behavior |
|--------|----------|
| `DENY` | Fail-closed when PDP unavailable (secure default) |
| `ALLOW` | Fail-open when PDP unavailable (dev/test only) |
| `FAIL` | Propagate the engine error to the caller |

### Dual-Principal Evaluation

When JWTs contain both user and workload identity, the engine can evaluate both:

| Mode | Behavior |
|------|----------|
| `PRIMARY_ONLY` | Evaluate only the primary (User) subject |
| `DUAL_AND` | Evaluate User + Workload; both must permit |
| `DUAL_OR` | Evaluate User + Workload; either may permit |

### Resilience

The policy engine integration includes circuit breaker and retry support:

```kotlin
data class ResilienceConfig(
    val circuitBreakerEnabled: Boolean = true,
    val failureThreshold: Int = 5,
    val resetTimeMs: Long = 30000,
    val retryEnabled: Boolean = false,
    val maxRetries: Int = 2,
    val retryDelayMs: Long = 100
)
```

### Step-Up Authentication (RFC 9470)

When a policy decision returns `STEP_UP_REQUIRED`, the transport layer returns HTTP 401 with a `WWW-Authenticate` challenge header:

```
WWW-Authenticate: Bearer error="insufficient_user_authentication", acr_values="urn:mace:incommon:iap:silver", max_age=300
```

This allows clients to re-authenticate at a higher assurance level before retrying.

## VDX Transport Layer Enforcement

In VDX, authorization is enforced at the transport level via `PolicyEnforcer`, which gates every command routed through `BinaryCommandAdapter`.

### Enforcement Flow

```
Incoming Request (REST/gRPC)
    │
    ▼
1. Authentication Extraction
    │  Extract & validate JWT/token from request
    │  Result: SessionContext with identity claims
    │
    ▼
2. Command Lookup
    │  Find the command in the registry by commandId
    │  Result: RegistrableServiceCommand
    │
    ▼
3. Policy Enforcement  ← Authorization gate
    │  PolicyEnforcer.enforce(sessionContext, command, resource, scope)
    │  Builds PolicyRequest with principal, action, resource, context
    │  Delegates to configured PolicyEngine (OPA, Cedarling, NoOp)
    │
    ├─ PERMIT  → Continue to step 4
    ├─ DENY    → Return 403 Forbidden
    └─ STEP_UP → Return 401 with WWW-Authenticate challenge (RFC 9470)
    │
    ▼
4. Input Decoding
    │
    ▼
5. Command Execution
    │
    ▼
6. Response Encoding
```

### PolicyEnforcer Interface

```kotlin
interface PolicyEnforcer {
    suspend fun enforce(
        sessionContext: SessionContext,
        command: RegistrableServiceCommand,
        resource: String? = null,
        scope: TransportScope = TransportScope.EXTERNAL
    ): IdkResult<Unit, IdkError>
}
```

The `scope` parameter distinguishes INTERNAL (gRPC between services) and EXTERNAL (REST from clients) requests. Scope is determined by the server layer, never from client-controlled metadata.

### Default Wiring

VDX uses DI to wire the policy enforcement chain:

1. `PolicyEngineEnforcer` (default `PolicyEnforcer` binding) delegates to `PolicyEngineEnforcementCommand`
2. `PolicyEngineEnforcementCommand` resolves the tenant-specific engine via `TenantPolicyEngineResolver`
3. The resolved `PolicyEngine` evaluates the request and returns a `PolicyDecision`
4. Fallback policy applies when the engine returns an error

When no real authorization module is on the classpath, `NoOpPolicyEngine` (always permits) is the default.

### Tenant-Aware Engine Resolution

Different tenants can use different policy engines:

```properties
# Default engine for all tenants
sphereon.authz.policy.default-engine=opa-main
sphereon.authz.policy.default-fallback=deny

# Engine definitions
sphereon.authz.policy.engines.opa-main.type=opa
sphereon.authz.policy.engines.opa-main.endpoint=http://opa:8181
sphereon.authz.policy.engines.opa-main.policy-path=/v1/data/sphereon/authz
sphereon.authz.policy.engines.opa-main.timeout-ms=5000

sphereon.authz.policy.engines.cedarling.type=cedarling
sphereon.authz.policy.engines.cedarling.base-url=http://cedarling:5000
sphereon.authz.policy.engines.cedarling.evaluation-path=/cedarling/evaluation

# Per-tenant overrides
sphereon.authz.policy.tenants.tenant-1.engine=opa-main
sphereon.authz.policy.tenants.tenant-2.engine=cedarling
```

## Policy Examples

### OPA Rego Policy

```rego
package sphereon.authz

default allow = false

# Allow all KMS read operations
allow {
    input.action.attributes.module == "kms"
    input.action.attributes.command == "get"
}

# Allow DID resolution for authenticated users
allow {
    input.action.name == "did.manager.resolve"
    input.principal.id != "anonymous"
}

# Allow party operations for specific tenant
allow {
    input.action.attributes.module == "party"
    input.context.tenantId == "acme-corp"
}
```

### Cedar Policy

```cedar
permit(
    principal == User::"acct-123",
    action == Action::"create",
    resource == Command::"party.parties.create"
);

permit(
    principal,
    action == Action::"get",
    resource
) when {
    resource.type == "command" && action.name like "kms.*"
};

forbid(
    principal,
    action,
    resource
) when {
    action.name like "eidas.*" && context.transport_scope == "EXTERNAL"
};
```

## ServiceCommand Integration

Service commands declare their identity via the `commandId` property:

```kotlin
interface GetKeyServiceCommand : ServiceCommand<GetKeyInput, KeyInfo> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ

    companion object {
        const val COMMAND_ID = "kms.keys.get"
    }
}
```

The `ServiceCommand` interface provides structured identity derived from `commandId`:

| Property | Derivation | Example for `kms.keys.get` |
|----------|-----------|----------------------------|
| `module` | First segment | `"kms"` |
| `service` | Second segment | `"keys"` |
| `command` | Third segment (last) | `"get"` |
| `actionType` | Overridden per command | `ActionType.READ` |
| `serviceId` | Everything before last dot | `"kms.keys"` |
| `operationName` | Last segment | `"get"` |
| `serviceDomain` | First segment | `"kms"` |

## Using Authorization in Command Extensions

### With IEnhancedCommandExecutionExtension

Authorization can also be enforced via command execution extensions (in addition to transport-level enforcement):

```kotlin
class AuthorizationExtension(
    private val authorizer: CommandAuthorizer
) : IEnhancedCommandExecutionExtension<Any, Any, IdkError> {

    override suspend fun beforeExecute(
        service: Command<Any, Any, IdkError>,
        args: Any
    ): BeforeExecuteResult<Any, Any, IdkError> {
        val commandId = CommandId.tryParse(service.id)
            ?: return BeforeExecuteResult.Continue(args)

        val authResult = authorizer.isAuthorized(commandId, sessionContext)
        return if (authResult.isOk) {
            BeforeExecuteResult.Continue(args)
        } else {
            BeforeExecuteResult.ShortCircuit(Err(authResult.error))
        }
    }
}
```

## Observability

Policy decisions are instrumented with metrics:

| Metric | Description |
|--------|-------------|
| `sphereon.authz.decision.count` | Decisions by tenant, engine, scope, and outcome |
| `sphereon.authz.engine.error.count` | Engine errors by tenant and error code |
| `sphereon.authz.fallback.activation.count` | Fallback activations by policy and source |
| `sphereon.authz.circuit.open.count` | Circuit breaker open events |

Audit events are recorded for authorization outcomes: `STARTED`, `AUTH_FAILURE`, `POLICY_DENIED`, `FAILED`.

## Error Handling

Authorization errors use the `COMMAND_NOT_AUTHORIZED` error code:

```kotlin
IdkError(
    code = "COMMAND_NOT_AUTHORIZED",
    message = IdkError.Message(
        i18nKey = "com.sphereon.core.error.command-not-authorized",
        defaultMessage = "Not authorized to execute 'kms.keys.get': Policy denied access"
    ),
    meta = mapOf(
        "commandId" to "kms.keys.get",
        "reason" to "Policy denied access",
        "actor" to "user-123"
    )
)
```

Create errors using the `CommandErrors` helper:

```kotlin
val error = CommandErrors.notAuthorized(
    commandId = CommandId("kms.keys.get"),
    reason = "Missing required role",
    actor = session.sessionId
)

// Check if an error is an authorization failure
CommandErrors.isAuthorizationFailure(error) // true
```

Policy engine denials at the transport level use `FORBIDDEN_ERROR`:

```kotlin
IdkError.FORBIDDEN_ERROR(message = "Access denied by policy")
```

## Best Practices

1. **Use three-segment IDs** — `module.service.command` enables clean pattern matching
2. **Start restrictive** — Default to `DENY` fallback and `ENFORCED` mode in production
3. **Exclude health checks** — Configure `excludePatterns` for `health.**`, `discovery.**`
4. **Use tenant-aware engines** — Different tenants may have different policy requirements
5. **Enable circuit breakers** — Prevent cascading failures when a PDP is down
6. **Log before enforcing** — Use `LOG_ONLY` mode during rollout to validate policies
7. **Scope-aware policies** — Apply different rules for INTERNAL vs EXTERNAL transport
8. **Cache decisions** — Enable caching with appropriate TTL for performance
