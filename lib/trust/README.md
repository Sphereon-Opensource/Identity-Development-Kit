# Trust Validation Infrastructure

This module provides a flexible infrastructure for trust validation that supports multiple trust models including ETSI Trusted Lists (TS 119 612), Certificate Authorities, DIDs, and OpenID Federation.

## Architecture

The trust validation infrastructure consists of three main components:

### 1. Core (`libraries/trust/core`)

The core module provides the generic trust validation framework that can be extended to support different trust models:

- **`TrustValidationService`**: Interface for validating trust in cryptographic credentials
- **`TrustListResolver`**: Interface for resolving trust lists from various sources (HTTP, file system, etc.)
- **`TrustContext`**: Defines the context in which validation should be performed
- **`TrustAnchor`**: Represents a trust anchor (root CA, TSP, DID document, etc.)
- **`TrustValidationResult`**: Contains the result of trust validation

### 2. ETSI (`libraries/trust/etsi`)

Implementation of ETSI TS 119 612 Trust Service Status Lists:

- **`ETSITrustList`**: Data model for ETSI trust lists based on TS 119 612 v2.4.1 (August 2024)
- **`ETSITrustListParser`**: Interface for parsing ETSI TSL XML (platform-specific implementations required)
- **`ETSITrustListResolver`**: Resolver for ETSI trust lists with support for EU LOTL
- **`ETSITrustValidator`**: Trust validator that validates certificates against ETSI trust lists
- **Schema Repository**: https://forge.etsi.org/rep/esi/x19_612_trusted_lists

### 3. Hosting (`libraries/trust/hosting`)

Service for hosting trust lists:

- **`TrustListHostingService`**: Interface for publishing and managing hosted trust lists
- **`InMemoryTrustListHostingService`**: Simple in-memory implementation

## Usage

### Validating Certificates Against ETSI Trust Lists

```kotlin
// Create the trust context
val context = TrustContext(
    type = TrustContext.TYPE_ETSI_TSL,
    framework = "https://ec.europa.eu/tools/lotl/eu-lotl.xml" // EU LOTL
)

// Create validation request
val request = TrustValidationRequest(
    certificateDER = certificateBytes,
    chainDER = chainBytes,
    context = context,
    checkRevocation = true
)

// Validate
val result = trustValidator.validate(request)

if (result.trusted) {
    println("Certificate is trusted by ${result.trustAnchor?.name}")
    println("Validation path: ${result.validationPath.joinToString(" -> ")}")
} else {
    println("Certificate is not trusted: ${result.details}")
}
```

### Resolving ETSI Trust Lists

```kotlin
// Resolve a specific country's trust list
val trustListData = etsiResolver.resolve(
    "https://www.acm.nl/sites/default/files/documents/tsl-nl.xml"
)

// Parse the trust list
val trustList = parser.parse(trustListData.data)

// Get all trust service providers
trustList.trustServiceProviders.forEach { tsp ->
    println("TSP: ${tsp.tspInformation.tspName["en"]}")
    tsp.tspServices.forEach { service ->
        println("  Service: ${service.serviceInformation.serviceName["en"]}")
        println("  Status: ${service.serviceInformation.serviceStatus}")
    }
}
```

### Hosting Your Own Trust List

```kotlin
// Create a trust list
val trustList = ETSITrustList(
    tslSequenceNumber = 1,
    tslType = "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric",
    schemeOperatorName = mapOf("en" to "My Organization"),
    schemeName = mapOf("en" to "My Trust Scheme"),
    // ... more fields
)

// Publish it
val uri = hostingService.publish(trustList, "/my-trust-list.xml")
println("Trust list published at: $uri")
```

## Integration with Existing Infrastructure

The trust validation infrastructure integrates seamlessly with existing components:

### X.509 Certificate Validation

The ETSI trust validator uses the existing `X509VerifyService` to validate certificate chains before checking against the trust list.

```kotlin
@Inject
class ETSITrustValidator(
    private val x509VerifyService: X509VerifyService,
    // ...
)
```

### Identifier Resolution

The trust list resolvers follow the same pattern as `KeyResolverService`, allowing for consistent resolution across the codebase:

```kotlin
interface TrustListResolver {
    fun getId(): String
    suspend fun resolve(uri: String, options: ResolutionOptions): TrustListData
    fun supports(uri: String): Boolean
}
```

### Key Management Integration

Trust anchors from ETSI trust lists can be used with the existing key management infrastructure:

```kotlin
val trustAnchors = trustValidator.getTrustAnchors()
trustAnchors.forEach { anchor ->
    // anchor.certificateDER can be used with X509VerifyService
    // anchor.metadata contains TSP information
}
```

## Extensibility

The framework is designed to support additional trust models:

### Adding CA Bundle Support

```kotlin
class CABundleTrustValidator(
    private val caBundlePath: String
) : AbstractTrustValidationService(
    id = "ca_bundle",
    supportedContextTypes = setOf(TrustContext.TYPE_CA_BUNDLE)
) {
    override suspend fun validate(request: TrustValidationRequest): TrustValidationResult {
        // Validate against CA bundle
    }
}
```

### Adding DID Support

```kotlin
class DIDTrustValidator(
    private val didResolver: DIDResolver
) : AbstractTrustValidationService(
    id = "did",
    supportedContextTypes = setOf(TrustContext.TYPE_DID)
) {
    override suspend fun validate(request: TrustValidationRequest): TrustValidationResult {
        // Resolve DID and validate
    }
}
```

### Adding OpenID Federation Support

```kotlin
class OIDCFederationTrustValidator(
    private val federationEndpoint: String
) : AbstractTrustValidationService(
    id = "openid_federation",
    supportedContextTypes = setOf(TrustContext.TYPE_OPENID_FEDERATION)
) {
    override suspend fun validate(request: TrustValidationRequest): TrustValidationResult {
        // Validate against federation
    }
}
```

## ETSI TS 119 612 Implementation Notes


### Well-Known Trust List Locations

The implementation includes well-known ETSI trust list locations:

- **EU LOTL**: `https://ec.europa.eu/tools/lotl/eu-lotl.xml`
- **Belgium**: `https://tsl.belgium.be/tsl-be.xml`
- **Netherlands**: `https://www.acm.nl/sites/default/files/documents/tsl-nl.xml`
- **Germany**: `https://www.bundesnetzagentur.de/tsl-de.xml`
- **France**: `https://ssi.gouv.fr/eidas/TL-FR.xml`

## Dependencies

The trust modules depend on:

- `libraries/core/api` - Core API and dependency injection
- `libraries/core/compat` - Cross-platform compatibility
- `libraries/crypto/core` - X.509 certificate validation
- Ktor HTTP client - For resolving remote trust lists
- kotlinx-datetime - For timestamp handling
- kotlinx-serialization - For data serialization


## Design Philosophy

The trust validation infrastructure is built on a key architectural principle: **Trust lists are treated as external identifiers**, just like X.509 certificates, DIDs, OIDC Discovery endpoints, and OpenID Federation entities.

This design makes the trust validation system:
- **Key-material agnostic**: Works with any cryptographic format
- **Resolution-agnostic**: Supports both external (HTTP) and managed (local) resolution
- **Consistent**: Follows the same patterns as existing identifier resolution
- **Extensible**: Easy to add new trust models (CA bundles, DIDs, OpenID Federation)

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│          MultiIdentifierResolutionService                       │
│  (Coordinates all identifier resolution - X.509, DID, ETSI, etc)│
└──────────────────┬──────────────────────────────────────────────┘
                   │
          ┌────────┴───────┐
          │                │
┌─────────▼──────────┐ ┌───▼───────────────────────┐
│ External Resolution│ │  Managed Resolution       │
│ (Remote resources) │ │  (Local storage)          │
└─────────┬──────────┘ └───┬───────────────────────┘
          │                │
    ┌─────┴────┐      ┌────┴────┐
    │          │      │         │
┌───▼────┐ ┌───▼──────▼───┐ ┌───▼─────────┐
│ X5c    │ │ ETSI TSL     │ │ Managed ETSI│
│ Service│ │ Service      │ │ TSL Service │
└────────┘ └──────────────┘ └─────────────┘
    │           │                 │
    │      ┌────▼────┐            │
    │      │ TSL     │            │
    │      │ Parser  │            │
    │      └─────────┘            │
    │           │                 │
    └───────────┴─────────────────┴───────────┐
                                              │
                                     ┌────────▼─────────┐
                                     │ X509VerifyService│
                                     └──────────────────┘
```

## How It Works

### 1. ETSI Trust Lists as External Identifiers

ETSI trust lists are implemented as external identifier services, following the same pattern as X.509 certificate chain resolution:

```kotlin
// X.509 resolution (existing)
val x5cOpts = ExternalIdentifierX5cOpts(
    identifier = listOf("cert1_base64", "cert2_base64"),
    trustAnchors = listOf("rootCA_base64")
)
val x5cResult = identifierService.resolve(x5cOpts)

// ETSI Trust List resolution (new, same pattern)
val etsiOpts = ExternalIdentifierETSITslOpts(
    identifier = "https://ec.europa.eu/tools/lotl/eu-lotl.xml",
    verifySignature = true,
    territory = "EU"
)
val etsiResult = identifierService.resolve(etsiOpts)
```

### 2. Identifier Methods

New identifier methods are added for ETSI trust validation:

```kotlin
object ETSIIdentifierMethods {
    val ETSI_TSL = object : IIdentifierMethod {
        override val value: String = "etsi_tsl"
        override val description: String = "ETSI TS 119 612 Trust Service Status List"
    }

    val ETSI_TSP = object : IIdentifierMethod {
        override val value: String = "etsi_tsp"
        override val description: String = "ETSI Trust Service Provider"
    }
}
```

These integrate seamlessly with existing methods:
- `IdentifierMethodDefaults.X5C` - X.509 certificate chains
- `IdentifierMethodDefaults.DID` - Decentralized Identifiers
- `IdentifierMethodDefaults.OIDC_DISCOVERY` - OIDC Discovery
- `IdentifierMethodDefaults.ENTITY_ID` - OpenID Federation
- `ETSIIdentifierMethods.ETSI_TSL` - **ETSI Trust Lists** (new)
- `ETSIIdentifierMethods.ETSI_TSP` - **ETSI Trust Service Providers** (new)

### 3. Resolution Flow

#### External Resolution (Remote Trust Lists)

1. **User Request**:
```kotlin
val opts = ExternalIdentifierETSITslOpts(
    identifier = "https://tsl.belgium.be/tsl-be.xml"
)
```

2. **Multi-Service Routing**:
    - `MultiIdentifierResolutionService` receives the request
    - Routes to `IMultiExternalIdentifierService`
    - Finds `ETSITrustListIdentifierResolutionService` (supports `etsi_tsl` method)

3. **Trust List Resolution**:
    - Service finds appropriate `TrustListResolver` (HTTP, file, etc.)
    - Resolver fetches the trust list (with caching)
    - `ETSITrustListParser` parses the XML
    - Extracts trust anchors and certificates

4. **Result**:
```kotlin
val result = etsiResult as ExternalIdentifierETSITslResult
result.trustList // Parsed ETSI trust list
result.trustAnchors // List of TrustAnchor objects
result.jwks // JWKs of all TSP certificates
result.keyInfo // Primary key info
```

#### Managed Resolution (Locally Hosted Trust Lists)

For locally hosted trust lists, the same pattern applies but uses managed identifier resolution:

```kotlin
val managedOpts = ManagedETSITrustListOpts(
    identifier = "my-local-tsl",  // Local identifier
    territory = "NL"
)
val managedResult = identifierService.resolve(managedOpts)
```

The hosted trust list is retrieved from local storage instead of being fetched via HTTP.

### 4. Integration with X.509 Validation

The ETSI trust list resolution integrates with X.509 certificate validation:

```kotlin
// Step 1: Resolve trust list to get trust anchors
val tslResult = identifierService.resolve(
    ExternalIdentifierETSITslOpts(
        identifier = "https://ec.europa.eu/tools/lotl/eu-lotl.xml"
    )
) as ExternalIdentifierETSITslResult

// Step 2: Extract trust anchor certificates
val trustAnchorCerts = tslResult.trustAnchors
    .mapNotNull { it.certificateDER }

// Step 3: Validate certificate against trust anchors
val x5cResult = identifierService.resolve(
    ExternalIdentifierX5cOpts(
        identifier = listOf(certToValidate),
        trustAnchors = trustAnchorCerts.map { it.encodeToBase64() },
        verify = true
    )
) as ExternalIdentifierResult.X5c

// Step 4: Check if certificate is trusted
val trusted = x5cResult.verificationResult.valid
```

## Key Benefits of This Approach

### 1. Agnostic to Key Material

The identifier resolution system doesn't care about the underlying key format:
- X.509 certificates → JWKs
- COSE keys → JWKs
- ETSI trust lists → JWKs
- DID documents → JWKs

All results provide a consistent `ResolvedKeyInfoType<KeyType>` interface.

### 2. Consistent Resolution Pattern

Whether resolving:
- An X.509 certificate chain from a JWT header
- A DID document from a distributed ledger
- An ETSI trust list from a government server
- An OpenID Federation entity configuration

The pattern is the same:
```kotlin
val opts = SomeIdentifierOpts(identifier = "...")
val result = identifierService.resolve(opts)
```

### 3. Flexible Storage

Trust lists can be resolved from:
- **External sources**: HTTP/HTTPS, IPFS, database
- **Managed storage**: Local files, in-memory, key-value store
- **Hybrid**: Cache external lists locally

The resolution mechanism handles this transparently.

### 4. Extensibility

Adding a new trust model (e.g., CA bundles, WebPKI, custom) follows the same pattern:

```kotlin
// 1. Define identifier method
object CustomIdentifierMethods {
    val MY_TRUST_MODEL = object : IIdentifierMethod {
        override val value = "my_trust_model"
    }
}

// 2. Define opts
data class ExternalIdentifierMyTrustModelOpts(...) : ExternalIdentifierOpts(...)

// 3. Define result
data class ExternalIdentifierMyTrustModelResult(...) : ExternalIdentifierResult(...)

// 4. Implement service
class MyTrustModelIdentifierResolutionService : ExternalIdentifierServiceAdapter<...> {
    // Implementation
}

// 5. Register via dependency injection (automatic with @ContributesBinding)
```


## Example: Complete Trust Validation Flow

```kotlin
// Application wants to validate a certificate against ETSI trust list
class MyApplication @Inject constructor(
    private val identifierService: IIdentifierService
) {
    suspend fun validateCertificate(certDER: ByteArray): Boolean {
        // 1. Resolve the ETSI trust list
        val tslResult = identifierService.resolve(
            ExternalIdentifierETSITslOpts(
                identifier = "https://ec.europa.eu/tools/lotl/eu-lotl.xml",
                verifySignature = true,
                useCache = true,
                territory = "EU",
                serviceTypeFilter = listOf("CA/QC") // Only qualified CAs
            )
        ) as ExternalIdentifierETSITslResult

        // 2. Get trust anchors
        val trustAnchors = tslResult.trustAnchors
            .mapNotNull { it.certificateDER?.encodeToBase64() }

        // 3. Validate certificate
        val x5cResult = identifierService.resolve(
            ExternalIdentifierX5cOpts(
                identifier = listOf(certDER.encodeToBase64()),
                trustAnchors = trustAnchors,
                verify = true
            )
        ) as ExternalIdentifierResult.X5c

        // 4. Check validation result
        return x5cResult.verificationResult.valid
    }
}
