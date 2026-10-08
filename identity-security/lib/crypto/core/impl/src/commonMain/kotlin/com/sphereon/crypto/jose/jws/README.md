# JWS/JWT Support Implementation

This package provides comprehensive JSON Web Signature (JWS) and JSON Web Token (JWT) support for the Sphereon crypto library, implemented as a Kotlin Multiplatform (KMP) module.

## Overview

The implementation follows the command pattern architecture used throughout the Sphereon IDK codebase. It provides both command-based and service-based APIs for creating and verifying JWS in all standard formats.

## Architecture

### Command Pattern

Each operation is implemented as a command that extends `ExecutionScopedCommandAdapter`:

- **PrepareJwsCommand**: Prepares a JWS object for signing (common functionality)
- **CreateJwsCompactCommand**: Creates compact JWS (`header.payload.signature`)
- **CreateJwsJsonFlattenedCommand**: Creates flattened JSON JWS
- **CreateJwsJsonGeneralCommand**: Creates general JSON JWS (supports multiple signatures)
- **VerifyJwsCommand**: Verifies JWS signatures

### Service Layer

`JwtService` provides a traditional service interface that internally uses the commands, offering both:
- Direct service methods (`createJwsCompact()`, `verifyJws()`, etc.)
- Command access via `service.commands.createJwsCompact`

## Key Features

### 1. Multiple JWS Serialization Formats

- **Compact**: `header.payload.signature` (most common, used in JWTs)
- **Flattened JSON**: Single signature in JSON format
- **General JSON**: Multiple signatures support

### 2. Identifier Resolution Integration

Uses the existing `com.sphereon.crypto.resolution` package:
- `ManagedIdentifierOptsOrResult` for signing operations
- `ExternalIdentifierService` for verification
- Supports multiple identifier types: DID, X5C, JWK, KID, Key Alias

### 3. Multiple Identifier Modes

- **DID**: Decentralized Identifiers (kid points to DID)
- **X5C**: X.509 certificate chain
- **JWK**: JSON Web Key embedded in header
- **KID**: Key identifier
- **AUTO**: Automatic selection based on identifier type

### 4. Key Management Integration

- Uses `RawSignatureService` for signing
- Uses `IManagedIdentifierService` for key resolution
- Leverages existing KMS providers (software, Azure, AWS, etc.)

## Data Types

### Core Types

```kotlin
// JWS formats
typealias JwsCompact = String
data class JwsJsonFlattened(payload, protected, header?, signature)
data class JwsJsonGeneral(payload, signatures)
data class JwsJsonSignature(protected, header?, signature)

// Prepared JWS
data class PreparedJws(protectedHeader, payload, unprotectedHeader?, existingSignatures?)
data class PreparedJwsObject(jws, b64, identifier)

// Validation
data class JwsValidationResult(jws, isValid, errorMessages, verificationTime)
```

### Request Types

```kotlin
data class CreateJwsArgs(
    mode: JwsIdentifierMode,
    issuer: IdentifierOptsOrResult,
    protectedHeader: JsonObject,
    payload: Any,
    // ... additional fields
)

data class VerifyJwsArgs(
    jws: Jws,
    jwk: Jwk?,
    x5cOpts: X5cVerificationOpts?,
    didOpts: DidVerificationOpts?
)
```

## Usage Examples

### Creating a Compact JWS (JWT)

```kotlin
// Using managed identifier (alias-based)
val issuer = ManagedOptsAlias(
    identifier = "my-signing-key",
    lookup = KeyInfo(alias = "my-signing-key")
)

val args = CreateJwsCompactArgs(
    mode = JwsIdentifierMode.AUTO,
    issuer = issuer,
    protectedHeader = JsonObject(mapOf(
        "alg" to JsonPrimitive("ES256"),
        "typ" to JsonPrimitive("JWT")
    )),
    payload = JsonObject(mapOf(
        "sub" to JsonPrimitive("user123"),
        "iat" to JsonPrimitive(System.currentTimeMillis() / 1000)
    ))
)

val result = jwtService.createJwsCompact(args)
if (result.isOk) {
    val jwt = result.value.jwt // "eyJ...header.eyJ...payload.sig...signature"
}
```

### Creating a JWS with X.509 Certificate Chain

```kotlin
val issuer = ManagedOptsX5c(
    identifier = listOf("cert1", "cert2"),  // Base64 DER certificates
    lookup = KeyInfo(x509CertificateChain = listOf("cert1", "cert2"))
)

val args = CreateJwsJsonGeneralArgs(
    mode = JwsIdentifierMode.X5C,
    issuer = issuer,
    protectedHeader = JsonObject(mapOf("alg" to JsonPrimitive("RS256"))),
    payload = myPayload
)

val result = jwtService.createJwsJsonGeneral(args)
```

### Verifying a JWS

```kotlin
val args = VerifyJwsArgs(
    jws = compactJwsString,  // or JwsJsonFlattened/JwsJsonGeneral
    x5cOpts = X5cVerificationOpts(verify = true)
)

val result = jwtService.verifyJws(args)
if (result.isOk) {
    val validation = result.value
    if (validation.isValid) {
        println("JWS is valid!")
        // Access resolved identifiers
        validation.jws.signatures.forEach { sig ->
            println("Verified with: ${sig.identifier}")
        }
    } else {
        println("Validation errors: ${validation.errorMessages}")
    }
}
```

### Using Commands Directly

```kotlin
// Access commands for more control
val prepareResult = jwtService.commands.prepareJws.execute(args, sessionContext)
if (prepareResult.isOk) {
    val prepared = prepareResult.value
    // Access prepared data: b64 encoded parts, identifier, etc.
}
```

## Integration Points

### Dependency Injection

Commands are designed to work with the Kotlin Inject DI framework:

```kotlin
@Inject
class PrepareJwsCommandImpl(
    execution: SessionExecution,
    private val identifierService: IManagedIdentifierService
) : ExecutionScopedCommandAdapter<...>(...)
```

### Session Management

All commands use `SessionExecution` for:
- Logging via `execution.log`
- Configuration via `execution.conf`
- Session context management

### Identifier Resolution

The implementation uses:
- `IManagedIdentifierService` for resolving signing keys
- `IExternalIdentifierService` for resolving verification keys
- `AdditionalIdentifierLookup` with `alias` as the primary lookup mechanism

## Comparison with TypeScript SDK

| TypeScript SDK | Kotlin Implementation |
|----------------|----------------------|
| Agent context with plugins | SessionExecution with DI |
| `IIdentifierResolution` | `IManagedIdentifierService` / `IExternalIdentifierService` |
| `IKeyManager` | `RawSignatureService` + KMS providers |
| Function-based | Command pattern |
| `ManagedIdentifierOptsOrResult` | Same concept, adapted to Kotlin |

## File Structure

```
com/sphereon/crypto/jose/jws/
├── JwsTypes.kt                          # Core data types
├── JwsCommands.kt                       # Command interfaces
├── JwsUtils.kt                          # Utility functions
├── JwtService.kt                        # Service interface & impl
├── command/
│   ├── PrepareJwsCommandImpl.kt
│   ├── CreateJwsCompactCommandImpl.kt
│   ├── CreateJwsJsonFlattenedCommandImpl.kt
│   ├── CreateJwsJsonGeneralCommandImpl.kt
│   └── VerifyJwsCommandImpl.kt
└── README.md                            # This file
```

## Testing

TODO: Add test implementations demonstrating:
- Compact JWS creation and verification
- JSON JWS formats
- Multiple signature scenarios
- Different identifier modes (DID, X5C, JWK)
- Error handling

## Future Enhancements

1. **JWE Support**: Implement encryption commands (currently only JWS/signing)
2. **Additional Algorithms**: Extend algorithm support as needed
3. **Performance Optimizations**: Caching, batch operations
4. **Enhanced Validation**: Additional RFC compliance checks
5. **Streaming Support**: For large payloads

## References

- [RFC 7515 - JSON Web Signature (JWS)](https://datatracker.ietf.org/doc/html/rfc7515)
- [RFC 7519 - JSON Web Token (JWT)](https://datatracker.ietf.org/doc/html/rfc7519)
- TypeScript SDK: `SSI-SDK/packages/jwt-service`
- Kiwa SDK Command Pattern: `kiwa-elicense-sdk/sdks/holder/sdk/impl`
