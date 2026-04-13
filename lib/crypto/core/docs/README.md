# lib-crypto-core

Kotlin Multiplatform cryptographic library providing JOSE (JSON Object Signing and Encryption) and COSE (CBOR Object Signing and Encryption) abstractions with pluggable Key Management System (KMS) providers.

## Supported Platforms

- JVM (Java 11+)
- JavaScript (Node.js, Browser)
- iOS (arm64, x64, simulator)

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Your Application                              │
├─────────────────────────────────────────────────────────────────┤
│  KeyManagerService  │  JwtService  │  IdentifierService         │
├─────────────────────────────────────────────────────────────────┤
│                    Cryptographic Operations                      │
│  Sign/Verify  │  Encrypt/Decrypt  │  Key Generation  │  ...     │
├─────────────────────────────────────────────────────────────────┤
│                    KMS Providers                                 │
│  Software  │  AWS KMS  │  Azure Key Vault  │  Hardware          │
├─────────────────────────────────────────────────────────────────┤
│                    Key Resolvers                                 │
│  DID Resolution  │  X.509 Chains  │  JWKS URLs                  │
├─────────────────────────────────────────────────────────────────┤
│                    Key Formats                                   │
│  JWK (JOSE)  │  CoseKey (CBOR)  │  X.509 Certificates           │
└─────────────────────────────────────────────────────────────────┘
```

## Core Concepts

### Key Management Service (KeyManagerService)

The central service for cryptographic operations. Manages KMS providers and key resolvers.

```kotlin
val kms: KeyManagerService = sessionComponent.asKeyManagerServiceComponent().kms

// Generate a key pair
val keyPair = kms.generateKey(
    alias = "signing-key-1",
    use = JwkUse.SIG,
    keyOperations = arrayOf(KeyOperations.SIGN),
    alg = SignatureAlgorithm.ECDSA_SHA256,
    keyVisibility = KeyVisibility.PRIVATE
)

// Query for providers by capability
val result = kms.queryProvider(kmsQuery {
    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
    storageType = KeyStorageType.EPHEMERAL
})
```

### Provider and Resolver Access

Access KMS providers directly when you need fine-grained control:

```kotlin
// Get a specific provider by ID
val awsProvider = kms.getProviderById("aws-kms")

// Get a provider that supports a specific algorithm
val provider = kms.getProvider(alg = SignatureAlgorithm.ECDSA_SHA256)

// List all available provider IDs
val providerIds = kms.getProviderIds()  // ["software", "aws-kms", "azure-kv"]

// Register a custom provider at runtime
kms.registerProvider(myCustomProvider, makeDefaultKms = true)
```

Access key resolvers for external identifier resolution:

```kotlin
// Get a resolver for DID-based keys
val didResolver = kms.getResolverByKeyTypeOrIdentifier(
    identifierMethod = IdentifierMethod.DID
)

// Get a resolver by ID
val resolver = kms.getResolverById("did-resolver")

// Register a custom resolver
kms.registerResolver(myResolver, makeDefaultResolver = false)
```

### KMS Providers

Implement cryptographic operations for different backends:

- **SoftwareKeyStore**: In-memory key generation and operations
- **MemoryKeyStore**: Transient key storage
- **AWS KMS Provider**: AWS Key Management Service integration
- **Azure Key Vault Provider**: Azure Key Vault integration
- **Hardware/Mobile Providers**: Platform-specific secure enclaves

### Key Types

#### Generic Key Abstractions

```kotlin
// KeyType - base interface for all key types
interface KeyType {
    fun getKeyType(): KeyTypeMapping
    fun getSignatureAlgorithm(): SignatureAlgorithm?
    fun toPublicKey(): KeyType
}

// KeyInfoType - metadata about a key
interface KeyInfoType<out KT : KeyType> {
    val key: KT?
    val kid: String?
    val alias: String?
    val providerId: String?
    fun identity(): KeyIdentity  // For cache/map keys
}

// ResolvedKeyInfoType - guaranteed to have a key
interface ResolvedKeyInfoType<out KT : KeyType> : KeyInfoType<KT> {
    override val key: KT  // Non-nullable
}
```

#### JOSE/COSE Conversion

```kotlin
// Convert between JOSE and COSE key formats
val jwk: Jwk = CoseJoseKeyMappingService.toJoseJwk(coseKey)
val cose: CoseKey = CoseJoseKeyMappingService.toCoseKey(jwk)

// Get resolved key info in JWK format
val jwkInfo: ResolvedKeyInfoType<JwkType> =
    CoseJoseKeyMappingService.toResolvedJwkKeyInfo(keyInfo)
```

#### Safe Conversion APIs (@Beta)

For error handling without exceptions, use the `tryXxx` variants that return `IdkResult`:

```kotlin
// Safe key conversion - returns IdkResult instead of throwing
val jwkResult: IdkResult<Jwk, IdkError> = CoseJoseKeyMappingService.tryToJoseJwk(coseKey)
val coseResult: IdkResult<CoseKey, IdkError> = CoseJoseKeyMappingService.tryToCoseKey(jwk)

// Handle the result
val jwk = jwkResult.getOrElse { error ->
    log.error("Conversion failed: ${error.message.defaultMessage}")
    return Err(error)
}

// Safe key info resolution
val resolvedResult = ResolvedKeyInfo.tryFromKeyInfo(keyInfo, key)
if (resolvedResult.isErr) {
    // Handle missing key gracefully
}
```

Available safe conversion functions:
- `CoseJoseKeyMappingService.tryToJoseJwk()` - Convert to JWK
- `CoseJoseKeyMappingService.tryToCoseKey()` - Convert to COSE key
- `CoseJoseKeyMappingService.tryToJoseX5c()` - Convert X.509 chain to JOSE format
- `CoseJoseKeyMappingService.tryToCoseX5chain()` - Convert X.509 chain to COSE format
- `ResolvedKeyInfo.tryFromKeyInfo()` - Create resolved key info safely

### Error Handling

All async operations return `IdkResult<T, IdkError>`:

```kotlin
val result = kms.queryProvider(query)

when {
    result.isOk -> {
        val provider = result.value.provider
        // Use provider
    }
    result.isErr -> {
        val error = result.error
        log.error("Query failed: ${error.message}")
    }
}

// Or use getOrElse for early return
val provider = result.getOrElse { error ->
    return Err(error)
}
```

### External Identifier Resolution

Resolve various identifier types to key material:

```kotlin
// DID resolution
val didOpts = ExternalIdentifierDidOpts(identifier = "did:example:123")

// JWK direct use
val jwkOpts = ExternalIdentifierJwkOpts(identifier = jwk)

// X.509 certificate chain
val x5cOpts = ExternalIdentifierX5cOpts(
    identifier = listOf(cert1Pem, cert2Pem),
    verify = true
)

// Using the resolver registry
val registry = ExternalIdentifierResolverRegistry(resolvers)
val result = registry.resolve(didOpts)
```

### Command Pattern

Operations use the command pattern for extensibility:

```kotlin
// Commands implement specific operations
interface SignCommand : Command<SignInput, SignOutput> {
    suspend fun execute(input: SignInput): IdkResult<SignOutput, IdkError>
}

// Commands are injected via DI
@Inject
class MyService(
    private val signCommand: SignCommand,
    private val verifyCommand: VerifyCommand
)
```

## DSL Builders

The library provides fluent DSL builders for common configuration scenarios:

### KMS Provider Query Builder

Query for KMS providers by capability:

```kotlin
val query = kmsQuery {
    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
    storageType = KeyStorageType.EPHEMERAL
    operation = KmsProviderOperation.SIGN
    keyType = KeyTypeMapping.EC
}

val result = kms.queryProvider(query)
```

### JWS Builders

Fluent builders for JWS header, payload, and options:

```kotlin
// Create a JWT using DSL builders
val args = createJwsArgs {
    issuer(managedKeyIdentifier)
    payload {
        iss("https://issuer.example.com")
        sub("user-123")
        aud("https://audience.example.com")
        exp(Clock.System.now().plus(1.hours).epochSeconds)
        custom("email", "user@example.com")
    }
    mode(JwsIdentifierMode.KID)
    options {
        protectedHeader {
            typ("JWT")
            alg("ES256")
        }
    }
}

val jwsResult = jwsService.createJwsCompact(args)
```

Individual builders are also available:

```kotlin
// Header builder
val header = jwsHeader {
    alg("ES256")
    typ("JWT")
    kid("key-123")
    x5c("cert1", "cert2")
}

// Payload builder
val payload = jwsPayload {
    iss("https://issuer.example.com")
    sub("user-123")
    exp(expireTime.epochSeconds)
    custom("role", "admin")
}

// Options builder
val opts = jwsOptions {
    noIssPayloadUpdate()
    protectedHeader(header)
}
```

### COSE Key Builder

Build COSE keys programmatically:

```kotlin
val coseKey = CoseKey.Builder()
    .ec2Key(CoseCurve.P_256, publicX, publicY)
    .kid("my-key-id")
    .algorithm(CoseAlgorithm.ES256)
    .keyOps(KeyOperations.SIGN, KeyOperations.VERIFY)
    .build()
```

## Dependency Injection

Uses kotlin-inject with Anvil for scope management:

```kotlin
// Scopes: AppScope -> UserScope -> SessionScope

// Inject KeyManagerService for high-level operations
@Inject
class MyService(
    private val kms: KeyManagerService
) {
    suspend fun sign(data: ByteArray): ByteArray {
        return kms.createRawSignature(data, keyInfo, algorithm)
    }
}

// Or inject specific interfaces for focused functionality
@Inject
class SigningService(
    private val providerRegistry: KmsProviderRegistry
) {
    fun getSigningProvider(algorithm: SignatureAlgorithm): KmsProvider {
        return providerRegistry.getProvider(alg = algorithm)
    }
}

@Inject
class KeyResolutionService(
    private val resolverRegistry: KeyResolverRegistry
) {
    fun resolveDidKey(did: String): KeyResolverService {
        return resolverRegistry.getResolverByKeyTypeOrIdentifier(
            identifierMethod = IdentifierMethod.DID
        )
    }
}
```

### Available Interfaces

| Interface | Purpose |
|-----------|---------|
| `KeyManagerService` | Full KMS facade - signing, encryption, key management |
| `KmsProviderRegistry` | Access to KMS providers by ID or capability |
| `KeyResolverRegistry` | Access to key resolvers for external identifiers |
| `ManagedKeyStoreService` | Key storage operations (list, get, store, delete) |
| `RawSignatureService` | Low-level signing and verification |
| `EncryptionService` | Encryption and decryption operations |

## Supported Algorithms

### Signature Algorithms

| Algorithm | Description | Key Type |
|-----------|-------------|----------|
| ES256 | ECDSA with P-256 and SHA-256 | EC |
| ES384 | ECDSA with P-384 and SHA-384 | EC |
| ES512 | ECDSA with P-521 and SHA-512 | EC |
| EdDSA | Edwards-curve DSA (Ed25519) | OKP |
| RS256 | RSASSA-PKCS1-v1_5 with SHA-256 | RSA |
| RS384 | RSASSA-PKCS1-v1_5 with SHA-384 | RSA |
| RS512 | RSASSA-PKCS1-v1_5 with SHA-512 | RSA |
| PS256 | RSASSA-PSS with SHA-256 | RSA |
| PS384 | RSASSA-PSS with SHA-384 | RSA |
| PS512 | RSASSA-PSS with SHA-512 | RSA |

### Key Encryption Algorithms

| Algorithm | Description |
|-----------|-------------|
| ECDH-ES | Elliptic Curve Diffie-Hellman Ephemeral Static |
| ECDH-ES+A128KW | ECDH-ES with AES-128 Key Wrap |
| ECDH-ES+A256KW | ECDH-ES with AES-256 Key Wrap |
| A128KW | AES-128 Key Wrap |
| A256KW | AES-256 Key Wrap |
| RSA-OAEP | RSAES OAEP |
| RSA-OAEP-256 | RSAES OAEP with SHA-256 |

### Content Encryption Algorithms

| Algorithm | Description |
|-----------|-------------|
| A128GCM | AES-128 GCM |
| A192GCM | AES-192 GCM |
| A256GCM | AES-256 GCM |
| A128CBC-HS256 | AES-128 CBC with HMAC-SHA-256 |
| A256CBC-HS512 | AES-256 CBC with HMAC-SHA-512 |

## Known Limitations

### RSA COSE Keys

RSA keys in COSE format (kty=3) are **not supported** for serialization/deserialization. While RSA algorithm identifiers exist in the COSE specification (RS256/-257, RS384/-258, RS512/-259, PS256/-37, PS384/-38, PS512/-39), the library does not implement RSA key CBOR encoding/decoding.

**What works:**
- RSA algorithm identifiers for COSE headers
- RSA signature algorithms in the `SignatureAlgorithm` enum
- RSA key type mapping (`KeyTypeMapping.RSA`)
- Full RSA support via JOSE (JWK) format

**What doesn't work:**
- Parsing RSA keys from COSE_Key CBOR structures
- Serializing RSA keys to COSE_Key CBOR format
- `CoseKey.fromCbor()` with RSA keys (throws `IllegalArgumentException`)

**Workaround:** Use JWK format for RSA keys:
```kotlin
// RSA operations should use JWK format
val rsaJwk = Jwk(
    kty = JwaKeyType.RSA,
    n = "...",  // RSA modulus (base64url)
    e = "AQAB" // RSA exponent (base64url)
)

// For COSE operations requiring RSA, use the algorithm identifier
// but handle key material through JWK
val signInput = SignInput(
    payload = data,
    keyInfo = KeyInfo(key = rsaJwk),
    signatureAlgorithm = SignatureAlgorithm.RSA_SHA256
)
```

### JWE JSON Serialization

JWE JSON serialization (flattened and general forms) is **not implemented**. The compact serialization format is fully supported.

**What works:**
- `CreateJweCompactCommand` - Full JWE compact serialization
- `DecryptJweCommand` - Decryption of compact JWE tokens
- `PrepareJweCommand` - Preparation of JWE headers and key info

**What doesn't work:**
- `CreateJweJsonFlattenedCommand` - Returns error (not implemented)
- `CreateJweJsonGeneralCommand` - Returns error (not implemented)

**Flattened JSON format** is intended for single-recipient scenarios where JSON format is preferred over compact:
```json
{
  "protected": "<base64url-protected-header>",
  "unprotected": { "alg": "..." },
  "encrypted_key": "<base64url-encrypted-cek>",
  "iv": "<base64url-iv>",
  "ciphertext": "<base64url-ciphertext>",
  "tag": "<base64url-auth-tag>"
}
```

**General JSON format** is intended for multi-recipient encryption:
```json
{
  "protected": "<base64url-protected-header>",
  "recipients": [
    { "header": {...}, "encrypted_key": "..." },
    { "header": {...}, "encrypted_key": "..." }
  ],
  "iv": "...",
  "ciphertext": "...",
  "tag": "..."
}
```

**Workaround:** Use compact serialization for all JWE operations:
```kotlin
// Use compact format (fully supported)
val jweCompact = jweService.createJweCompact(CreateJweCompactArgs(
    preparedJwe = preparedJwe
))

// Result: "eyJhbGciOiJFQ0RILUVTK0EyNTZLVyIsImVuYyI6IkEyNTZHQ00ifQ..."
```

### Platform Differences

#### JavaScript/TypeScript

- **Async callbacks**: JS uses callback-based crypto providers. Register callbacks before use:
  ```typescript
  import { DefaultCallbacks } from 'lib-crypto-core'

  // Set up JOSE callbacks
  DefaultCallbacks.setJoseCryptoDefault(myJoseCallbacks)

  // Set up COSE callbacks
  DefaultCallbacks.setCoseCryptoDefault(myCoseCallbacks)
  ```

- **JWK interop**: Use `JwkType` interface for plain JS objects instead of `Jwk` class constructors:
  ```typescript
  // Preferred - use interface
  const jwk: JwkType = { kty: 'EC', crv: 'P-256', x: '...', y: '...' }

  // Convert to Jwk class when needed
  const jwkInstance = Jwk.from(jwk)
  ```

- **Module exports**: Some internal classes are not exported to JS. Check `@JsExport` annotations.

#### iOS/Apple

- **ObjC naming**: Classes use `@ObjCName` for Swift/ObjC interoperability:
  ```swift
  // Swift usage
  let keyInfo = KeyInfo(kid: "my-key", key: jwk)
  let managedKey = ManagedKeyInfo(alias: "alias", providerId: "provider", resolvedKeyInfo: resolved)
  ```

- **Keychain integration**: Use `SoftwareKeyStore` with Apple Keychain for secure key storage on iOS/macOS.

- **Entropy**: Key generation uses `SecRandomCopyBytes` for cryptographic randomness.

#### JVM

- **Provider selection**: Multiple JCE providers can be used. Configure via `CryptographyProvider`:
  ```kotlin
  // Use specific JCE provider
  val provider = CryptographyProvider.Default // or specify custom
  ```

- **X.509**: Full support for certificate parsing and chain validation via `java.security`.

- **Thread safety**: Key stores are thread-safe. Use appropriate scoping (AppScope vs SessionScope) for shared state.

## Migration Guide

### From Legacy to Modern APIs

#### Deprecated Key Generation

Legacy methods (ending in `Async`) have been deprecated. Use the modern versions:

```kotlin
// Deprecated - will be removed in a future release
// val keyPair = kms.generateKeyAsync(...)

// Modern - use generateKey instead
val keyPair = kms.generateKey(
    alias = "my-key",
    alg = SignatureAlgorithm.ECDSA_SHA256
)
```

#### Deprecated Provider Capability Methods

The following `KmsProvider` methods are deprecated:

| Deprecated Method | Replacement |
|-------------------|-------------|
| `supportedKeyTypes()` | `getCapabilities().supportedKeyTypes` |
| `supportedSignatureAlgorithms()` | `getCapabilities().signatureAlgorithms` |
| `supportedDigests()` | `getCapabilities()` |
| `supportedCurves()` | `getCapabilities().supportedCurves` |

```kotlin
// Deprecated
// val keyTypes = provider.supportedKeyTypes()

// Modern - use getCapabilities()
val capabilities = provider.getCapabilities()
val keyTypes = capabilities.supportedKeyTypes
val algorithms = capabilities.signatureAlgorithms
```

#### Using IdkResult for Error Handling

Query operations return `IdkResult` for proper error handling:

```kotlin
val result = kms.queryProvider(query)
val provider = result.getOrElse { error ->
    log.error("Query failed: ${error.message.defaultMessage}")
    return Err(error)
}
```

### Using Safe Conversion APIs

For operations that may fail, prefer the `tryXxx` variants over throwing methods:

```kotlin
// Instead of catching exceptions:
// try {
//     val jwk = CoseJoseKeyMappingService.toJoseJwk(key)
// } catch (e: IllegalArgumentException) { ... }

// Use the safe variant:
val result = CoseJoseKeyMappingService.tryToJoseJwk(key)
when {
    result.isOk -> processKey(result.value)
    result.isErr -> handleError(result.error)
}
```

### Using KeyIdentity for Caches

Instead of relying on `equals()`/`hashCode()`:

```kotlin
// Use KeyIdentity for deterministic caching
val cache = mutableMapOf<KeyIdentity, CachedValue>()
cache[keyInfo.identity()] = value
```

## API Stability

APIs marked with these annotations have special stability guarantees:

- **@Beta**: API may change in minor versions
- **@Experimental**: API may change or be removed

Stable APIs follow semantic versioning.

## Building

```bash
# Build all targets
./gradlew :lib-crypto-core:build

# Run tests
./gradlew :lib-crypto-core-impl:allTests

# Build specific platforms
./gradlew :lib-crypto-core-public:jvmJar
./gradlew :lib-crypto-core-public:jsNodeProductionLibraryDistribution
```

## License

Apache License 2.0
