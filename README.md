<p align="center">
  <a href="https://sphereon.com">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://sphereon.com/content/themes/sphereon/assets/img/logo-wit.svg">
      <source media="(prefers-color-scheme: light)" srcset="https://avatars.githubusercontent.com/u/33261381?s=200&v=4">
      <img alt="Sphereon" src="https://avatars.githubusercontent.com/u/33261381?s=200&v=4" width="120">
    </picture>
  </a>
</p>

<h1 align="center">Identity Development Kit (IDK)</h1>

<p align="center">
  <strong>A Kotlin Multiplatform SDK for Digital Identity</strong>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Platform-Android%20|%20iOS%20|%20JVM%20|%20JS-brightgreen.svg" alt="Platforms">
  <img src="https://img.shields.io/badge/Version-0.13.1--SNAPSHOT-orange.svg" alt="Version">
</p>

<p align="center">
  <a href="#features">Features</a> &nbsp;&bull;&nbsp;
  <a href="#quick-start">Quick Start</a> &nbsp;&bull;&nbsp;
  <a href="#library-modules">Modules</a> &nbsp;&bull;&nbsp;
  <a href="#documentation">Docs</a> &nbsp;&bull;&nbsp;
  <a href="#contributing">Contributing</a>
</p>

---

## Overview

**IDK** is an open-source, open-core Kotlin Multiplatform SDK designed to simplify the development of identity wallets, verifiers, and issuers. Build once, deploy everywhere — Android, iOS, JVM, and JavaScript.

Whether you're implementing mobile driver's licenses, digital identity wallets, or verifiable credential solutions, IDK provides the building blocks you need.

---

## Features

<table>
<tr>
<td width="50%">

**Decentralized Identifiers (DIDs)**
- Create, resolve, and manage W3C DIDs
- Pluggable DID method support
- Built-in: `did:key`, `did:jwk`, `did:web`

**Mobile Documents (mDoc/mDL)**
- ISO 18013-5 compliant
- BLE and NFC transport layers
- Reader and holder functionality

**Cryptographic Services**
- JWS, JWE, JWT support
- KMS provider abstraction
- AWS, Azure, Mobile Keystores

</td>
<td width="50%">

**Verifiable Credentials**
- OpenID4VP (OID4VP) support
- SD-JWT selective disclosure
- DCQL query language

**OAuth2 / OpenID Connect**
- Client implementation
- Authorization server support
- JWT validation utilities

**Developer Experience**
- Compile-time dependency injection
- Modular architecture
- Spring Boot and Ktor integration

</td>
</tr>
</table>

---

## Supported Platforms

| Platform | Status | Notes |
|:---------|:------:|:------|
| Android | Supported | API 27+ (Android 8.1+) |
| iOS | Supported | arm64, x64, simulator |
| JVM | Supported | Java 17+ |
| JavaScript | Partial | Node.js |

---

## Quick Start

### Prerequisites

- **JDK 17** or higher
- **Gradle 8.x** (wrapper included)
- **Android SDK** — compileSdk 35 (for Android targets)
- **Xcode 15+** (for iOS targets, macOS only)

### Installation

Add the Sphereon Maven repository and dependencies to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven("https://nexus.sphereon.com/repository/sphereon-opensource-releases/")
    maven("https://nexus.sphereon.com/repository/sphereon-opensource-snapshots/")
}

dependencies {
    // Core
    implementation("com.sphereon.idk:lib-core-api-public:0.13.1-SNAPSHOT")

    // Cryptography
    implementation("com.sphereon.idk:lib-crypto-core:0.13.1-SNAPSHOT")

    // DIDs
    implementation("com.sphereon.idk:lib-did-resolver-impl:0.13.1-SNAPSHOT")
    implementation("com.sphereon.idk:lib-did-methods-key:0.13.1-SNAPSHOT")

    // mDoc
    implementation("com.sphereon.idk:lib-mdoc-core:0.13.1-SNAPSHOT")

    // Add other modules as needed...
}
```

### Building from Source

```bash
# Clone the repository
git clone https://github.com/Sphereon-Opensource/idk.git
cd idk

# Build all modules
./gradlew build

# Run all tests
./gradlew allTests
```

**Windows users:** If you encounter file locking issues, run `./gradlew --stop` before building.

---

## Library Modules

IDK is organized into focused, single-purpose modules. Use only what you need.

<details>
<summary><b>Core</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-core-api-public` | Core interfaces, `IdkResult`, command pattern, error types |
| `lib-core-api-default` | Default implementations of core APIs |
| `lib-core-events` | Event system for pub/sub messaging |
| `lib-core-compat` | Compatibility utilities |
| `lib-conf-settings` | Multiplatform configuration management |

</details>

<details>
<summary><b>Cryptography</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-crypto-core` | Core crypto operations, JWS, JWE, JWT |
| `lib-crypto-kms-provider-software` | Software-based key storage (ephemeral) |
| `lib-crypto-kms-provider-aws` | AWS KMS integration |
| `lib-crypto-kms-provider-azure` | Azure Key Vault / HSM integration |
| `lib-crypto-kms-provider-mobile` | iOS Keychain / Android Keystore |
| `lib-crypto-kms-provider-rest` | Remote KMS via REST API |
| `lib-crypto-kms-rest-server` | KMS REST server implementation |

</details>

<details>
<summary><b>Decentralized Identifiers (DID)</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-did-core-public` | DID data models and interfaces |
| `lib-did-resolver` | Universal DID resolver |
| `lib-did-manager` | DID lifecycle management |
| `lib-did-methods-key` | `did:key` method support |
| `lib-did-methods-jwk` | `did:jwk` method support |
| `lib-did-methods-web` | `did:web` method support |
| `lib-did-persistence-memory` | In-memory DID storage |
| `lib-did-persistence-sqlite` | SQLite DID storage |

</details>

<details>
<summary><b>Mobile Documents (mDoc/mDL)</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-mdoc-core` | ISO 18013-5 mDoc parsing and creation |
| `lib-mdoc-datatransfer` | Device engagement and session handling |
| `lib-mdoc-transport-ble` | Bluetooth Low Energy transport |
| `lib-mdoc-transport-nfc` | NFC transport (Android) |
| `lib-mdoc-transport-oid4vp` | OID4VP transport integration |
| `lib-mdoc-reader` | mDoc reader/verifier functionality |

</details>

<details>
<summary><b>OpenID / OAuth2</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-openid-oid4vp-common` | Shared OID4VP types |
| `lib-openid-oid4vp-holder` | Wallet/holder OID4VP support |
| `lib-openid-oid4vp-verifier` | Verifier OID4VP support |
| `lib-openid-oid4vp-dcql` | DCQL query language |
| `lib-oauth2-client` | OAuth2 client implementation |
| `lib-oauth2-server-authorization` | OAuth2 authorization server |
| `lib-oauth2-server-resource` | OAuth2 resource server |
| `lib-oauth2-jwt-validation` | JWT validation utilities |

</details>

<details>
<summary><b>SD-JWT</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-sdjwt-public` | SD-JWT interfaces and types |
| `lib-sdjwt-impl` | Selective Disclosure JWT implementation |

</details>

<details>
<summary><b>Data Link / Transport</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-data-link-ble` | BLE communication layer |
| `lib-data-link-nfc` | NFC communication layer |
| `lib-data-link-http-client` | HTTP client abstraction |

</details>

<details>
<summary><b>Storage</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-data-store-kv` | Key-value storage abstraction |
| `lib-data-store-kv-impl-memory` | In-memory KV store |
| `lib-data-store-kv-impl-kottage` | Persistent KV store (Kottage) |
| `lib-data-store-party` | Party/tenant data models |

</details>

<details>
<summary><b>Trust and Utilities</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-trust-core` | Trust framework core |
| `lib-trust-etsi` | ETSI trust list support |
| `lib-cbor` | CBOR encoding/decoding |
| `spring-support` | Spring Boot integration |
| `ktor-support` | Ktor server integration |

</details>

---

## Architecture

### Module Structure

IDK follows a clean separation between APIs and implementations:

```
lib/<domain>/
  ├── public/     # Interfaces, data models, contracts
  └── impl/       # Implementations (swappable)
```

### Error Handling

IDK uses `IdkResult<V, E>` for explicit error handling instead of exceptions:

```kotlin
import com.sphereon.core.api.Ok
import com.sphereon.core.api.Err
import com.sphereon.core.api.error.IdkError

fun createDid(): IdkResult<String, IdkError> {
    // Success case
    return Ok("did:key:z6Mk...")

    // Error case
    return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid key type"))
}

// Usage
when (val result = createDid()) {
    is Ok -> println("Created: ${result.value}")
    is Err -> println("Failed: ${result.error.message}")
}
```

### Dependency Injection

IDK uses [kotlin-inject](https://github.com/evant/kotlin-inject) with [kotlin-inject-anvil](https://github.com/amzn/kotlin-inject-anvil) for compile-time DI across all platforms.

Scopes: `AppScope` → `UserScope` → `SessionScope`

---

## Documentation

Generate API documentation:

```bash
./gradlew dokkaGenerate
```

Output: `build/dokka/html/index.html`

### Build Commands Reference

| Command | Description |
|:--------|:------------|
| `./gradlew build` | Build all modules |
| `./gradlew allTests` | Run all tests |
| `./gradlew jvmTest` | Run JVM tests only |
| `./gradlew testDebugUnitTest` | Run Android unit tests |
| `./gradlew iosSimulatorArm64Test` | Run iOS simulator tests |
| `./gradlew dokkaGenerate` | Generate API documentation |
| `BUILD_XCFRAMEWORKS=true ./gradlew build` | Build with iOS XCFrameworks |

---

## Related Projects

**EDK (Enterprise Development Kit)**

EDK extends IDK with enterprise-grade features for production deployments. It adds multi-tenancy, advanced persistence layers, tenant-aware repositories, audit logging, and integration with enterprise infrastructure. If you're building commercial identity solutions or need features like tenant isolation, user management, and compliance tooling, EDK builds on top of IDK to provide these capabilities.

**VDX (Verifiable Data Exchange)**

VDX is the API platform layer that sits on top of EDK/IDK. It provides ready-to-deploy REST APIs for credential issuance, verification, and wallet interactions. VDX includes OpenAPI specifications, storage backends, and deployment configurations for running identity services at scale. It's designed for organizations that want to operate identity infrastructure without building everything from scratch.

---

## Contributing

Contributions are welcome. Please ensure:

1. Code follows Kotlin coding conventions
2. All tests pass (`./gradlew allTests`)
3. New features include appropriate tests
4. Public APIs are documented

---

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

---

<p align="center">
  <sub>Built by <a href="https://sphereon.com">Sphereon</a> — Creating Trust In A Digital World</sub>
</p>

<p align="center">
  <a href="https://sphereon.com">Website</a> &nbsp;&bull;&nbsp;
  <a href="https://github.com/Sphereon-Opensource">GitHub</a>
</p>
