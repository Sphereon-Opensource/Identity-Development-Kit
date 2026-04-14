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
  <img src="https://img.shields.io/badge/Kotlin-2.3+-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Platforms-Android%20%7C%20iOS%20%7C%20JVM%20%7C%20JS%20%7C%20wasmJs%20%7C%20Linux-brightgreen.svg" alt="Platforms">
  <img src="https://img.shields.io/badge/Version-0.25.0--SNAPSHOT-orange.svg" alt="Version">
</p>

<p align="center">
  <a href="#features">Features</a> &nbsp;&bull;&nbsp;
  <a href="#quick-start">Quick Start</a> &nbsp;&bull;&nbsp;
  <a href="#library-modules">Modules</a> &nbsp;&bull;&nbsp;
  <a href="#services">Services</a> &nbsp;&bull;&nbsp;
  <a href="#documentation">Docs</a> &nbsp;&bull;&nbsp;
  <a href="#contributing">Contributing</a>
</p>

---

## Overview

IDK is an open-core Kotlin Multiplatform SDK for digital identity. It covers credential issuance and verification, identity proofing, trust establishment, and secure data exchange. One codebase compiles to Android, iOS, JVM, JavaScript (browser and Node.js), WebAssembly, and Linux native.

You can use IDK on its own to build wallets, verifiers, issuers, and identity services. It also serves as the foundation under Sphereon's commercial [Enterprise Development Kit (EDK)](https://docs.sphereon.com/edk/guides/getting-started).

Full documentation: [docs.sphereon.com/idk](https://docs.sphereon.com/idk/introduction).

---

## Features

Each item links to its guide on the documentation site.

<table>
<tr>
<td width="50%">

**[Decentralized Identifiers (DIDs)](https://docs.sphereon.com/idk/guides/did/overview)**
- Create, [resolve](https://docs.sphereon.com/idk/guides/did/resolution), and [manage](https://docs.sphereon.com/idk/guides/did/management) W3C DIDs
- Pluggable DID method support
- Built-in: `did:key`, `did:jwk`, `did:web`

**[Mobile Documents (mDoc / mDL)](https://docs.sphereon.com/idk/guides/mdoc/overview)**
- ISO/IEC 18013-5 compliant
- [BLE](https://docs.sphereon.com/idk/guides/mdoc/transports/ble), [NFC](https://docs.sphereon.com/idk/guides/mdoc/transports/nfc), and [HTTP/WebSocket](https://docs.sphereon.com/idk/guides/mdoc/transports/http-websocket) transports
- [Engagement](https://docs.sphereon.com/idk/guides/mdoc/engagement/intro) and [transfer](https://docs.sphereon.com/idk/guides/mdoc/transfer/transfer-manager) managers
- Reader and holder functionality

**[Cryptographic Services](https://docs.sphereon.com/idk/guides/crypto/key-management)**
- [JOSE / COSE](https://docs.sphereon.com/idk/guides/crypto/cose-jose) (JWS, JWE, JWT)
- [Signing and verification](https://docs.sphereon.com/idk/guides/crypto/signing-verification)
- Pluggable [KMS providers](https://docs.sphereon.com/idk/guides/crypto/kms-providers): AWS, Azure, mobile keystores, REST
- [Identifier resolution](https://docs.sphereon.com/idk/guides/crypto/identifier-resolution) (DID, x5c, JWK)

**[Trust Management](https://docs.sphereon.com/idk/guides/trust/overview)**
- [ETSI trust lists](https://docs.sphereon.com/idk/guides/trust/etsi-trust-lists)
- [X.509 certificate validation](https://docs.sphereon.com/idk/guides/trust/certificate-validation)
- [DID-based trust](https://docs.sphereon.com/idk/guides/trust/did-trust)
- [OpenID Federation](https://docs.sphereon.com/idk/guides/trust/openid-federation)

</td>
<td width="50%">

**[OpenID for Verifiable Presentations (OID4VP)](https://docs.sphereon.com/idk/guides/oid4vp/overview)**
- [Holder](https://docs.sphereon.com/idk/guides/oid4vp/holder) and [verifier](https://docs.sphereon.com/idk/guides/oid4vp/verifier) implementations
- [DCQL query language](https://docs.sphereon.com/idk/guides/oid4vp/dcql)
- [Universal request handling](https://docs.sphereon.com/idk/guides/oid4vp/universal)

**[OpenID for Verifiable Credential Issuance (OID4VCI)](https://docs.sphereon.com/idk/guides/oid4vci/overview)**
- [Issuer](https://docs.sphereon.com/idk/guides/oid4vci/issuer) and [holder](https://docs.sphereon.com/idk/guides/oid4vci/holder) implementations
- Authorization code, pre-authorized code, deferred issuance
- Notification endpoint

**[SD-JWT](https://docs.sphereon.com/idk/guides/sdjwt/overview)**
- [Selective disclosure issuance](https://docs.sphereon.com/idk/guides/sdjwt/issuance)
- Verification, key binding, status lists

**[OAuth 2.0 / OpenID Connect](https://docs.sphereon.com/idk/guides/oauth2/client)**
- [OAuth2 client](https://docs.sphereon.com/idk/guides/oauth2/client)
- [Authorization server](https://docs.sphereon.com/idk/guides/oauth2/authorization-server) (auth-code, client-credentials, pre-authorized-code, token exchange, introspection, revocation, PAR)
- [DPoP and PKCE](https://docs.sphereon.com/idk/guides/oauth2/dpop-pkce)
- [JWT validation](https://docs.sphereon.com/idk/guides/oauth2/jwt-validation)

**[Identity Proofing and Reconciliation](https://docs.sphereon.com/idk/guides/identity/overview)**
- Document verification, biometrics, OTP
- Policy-driven [identity reconciliation](https://docs.sphereon.com/idk/guides/identity/overview)

**[Developer Experience](https://docs.sphereon.com/idk/guides/di/scopes)**
- Compile-time [dependency injection](https://docs.sphereon.com/idk/guides/di/app-setup) via [Metro](https://github.com/ZacSweers/metro)
- [Configuration system](https://docs.sphereon.com/idk/guides/config/configuration) with [multi-tenancy](https://docs.sphereon.com/idk/guides/config/multi-tenancy)
- [Event system](https://docs.sphereon.com/idk/guides/core/events), [scoped logging](https://docs.sphereon.com/idk/guides/core/logging/overview)
- [Ktor](https://docs.sphereon.com/idk/guides/http/ktor) integration

</td>
</tr>
</table>

---

## Supported Platforms

| Platform | Status | Notes |
|:---------|:------:|:------|
| JVM | Supported | Java 21+ |
| Android | Supported | API 27+ (Android 8.1+); BLE, NFC HCE, Android Keystore |
| iOS | Supported | arm64, x64, simulator-arm64; CoreBluetooth, CoreNFC, Secure Enclave |
| JavaScript | Supported | Browser and Node.js, ES modules, TypeScript definitions generated |
| WebAssembly | Supported | wasmJs (browser and Node.js), BigInt, TypeScript definitions |
| Linux | Supported | x64, server and CLI tooling |

See [Platform Setup](https://docs.sphereon.com/idk/guides/platform-setup) for per-platform requirements.

---

## Quick Start

Full installation and getting-started instructions: [docs.sphereon.com/idk/guides/getting-started](https://docs.sphereon.com/idk/guides/getting-started).

### Prerequisites

- JDK 21 or higher
- Gradle 9.x (wrapper included)
- Android SDK, compileSdk 35 (for Android targets)
- Xcode 15+ (for iOS targets, macOS only)

### Installation

Add the Sphereon Maven repository and dependencies to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven("https://nexus.sphereon.com/repository/sphereon-opensource-releases/")
    maven("https://nexus.sphereon.com/repository/sphereon-opensource-snapshots/")
}

dependencies {
    // Core API
    implementation("com.sphereon.idk:lib-core-api-public:0.25.0-SNAPSHOT")

    // Cryptography
    implementation("com.sphereon.idk:lib-crypto-core-public:0.25.0-SNAPSHOT")
    implementation("com.sphereon.idk:lib-crypto-core-impl:0.25.0-SNAPSHOT")

    // DIDs
    implementation("com.sphereon.idk:lib-did-resolver-impl:0.25.0-SNAPSHOT")
    implementation("com.sphereon.idk:lib-did-methods-key:0.25.0-SNAPSHOT")

    // mDoc
    implementation("com.sphereon.idk:lib-mdoc-core-public:0.25.0-SNAPSHOT")
    implementation("com.sphereon.idk:lib-mdoc-core-impl:0.25.0-SNAPSHOT")

    // OID4VCI / OID4VP / SD-JWT: add as needed
}
```

See the [installation guide](https://docs.sphereon.com/idk/guides/installation) for per-platform setup and the [modules reference](https://docs.sphereon.com/idk/guides/modules) for the full module catalogue.

### npm packages

JavaScript and WebAssembly artefacts are published to npmjs under the `@sphereon/idk-*` scope. For example:

```bash
npm install @sphereon/idk-lib-core-api-public
```

Snapshots use the `snapshot` dist-tag (`@sphereon/idk-foo@snapshot`); released versions are on `latest`.

### Building from Source

```bash
git clone https://github.com/Sphereon-Opensource/Identity-Development-Kit.git
cd Identity-Development-Kit
./gradlew build                     # full multiplatform build
./gradlew build -Dkmp.targets=jvm   # JVM only (~5 min)
./gradlew allTests                  # run all tests
```

On Windows, run `./gradlew --stop` before rebuilding if you hit file-locking errors.

---

## Library Modules

Modules follow the `lib-<domain>-<feature>-{public,impl}` convention. `public` modules contain interfaces and data models; `impl` modules contain implementations.

Full module reference: [docs.sphereon.com/idk/guides/modules](https://docs.sphereon.com/idk/guides/modules).

<details>
<summary><b>Core</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-core-api-public` | Core interfaces, `IdkResult`, command pattern, error types, tracing |
| `lib-core-api-default` | Default implementations of core APIs |
| `lib-core-events-public` / `-impl` | [Event system](https://docs.sphereon.com/idk/guides/core/events) for pub/sub |
| `lib-core-loggers-mobile-logger` | Mobile-friendly logger sink |
| `lib-conf-settings` | Multiplatform [configuration management](https://docs.sphereon.com/idk/guides/config/configuration) |
| `lib-conf-yaml` | YAML config source |

</details>

<details>
<summary><b>Cryptography</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-crypto-core-public` / `-impl` | Core crypto operations, JWS, JWE, JWT |
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
| `lib-did-resolver-public` / `-impl` | Universal DID resolver |
| `lib-did-manager-public` / `-impl` | DID lifecycle management |
| `lib-did-methods-key` | `did:key` method support |
| `lib-did-methods-jwk` | `did:jwk` method support |
| `lib-did-methods-web` | `did:web` method support |
| `lib-did-persistence-memory` | In-memory DID storage |
| `lib-did-persistence-sqlite` | SQLite DID storage |

</details>

<details>
<summary><b>Mobile Documents (mDoc / mDL)</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-mdoc-core-public` / `-impl` | ISO/IEC 18013-5 mDoc parsing and creation |
| `lib-mdoc-datatransfer` | Device engagement and session handling |
| `lib-mdoc-transport-ble` | Bluetooth Low Energy transport |
| `lib-mdoc-transport-nfc` | NFC transport (Android) |
| `lib-mdoc-transport-oid4vp` | OID4VP transport integration |
| `lib-mdoc-reader` | mDoc reader/verifier functionality |

</details>

<details>
<summary><b>OpenID4VP / OpenID4VCI / OAuth2</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-openid-oid4vp-common-public` | Shared OID4VP types |
| `lib-openid-oid4vp-holder-public` / `-impl` | Wallet/holder OID4VP support |
| `lib-openid-oid4vp-verifier-public` / `-impl` | Verifier OID4VP support |
| `lib-openid-oid4vp-dcql` | DCQL query language |
| `lib-openid-oid4vci-common-public` | Shared OID4VCI types |
| `lib-openid-oid4vci-holder-public` / `-impl` | Holder OID4VCI flow |
| `lib-openid-oid4vci-issuer-public` / `-impl` | Issuer OID4VCI flow |
| `lib-oauth2-client-public` / `-impl` | OAuth2 client implementation |
| `lib-oauth2-server-authorization-public` / `-impl` | OAuth2 authorization server |
| `lib-oauth2-server-resource-public` / `-impl` | OAuth2 resource server |
| `lib-oauth2-jwt-validation-api` / `-impl` | JWT validation utilities |

</details>

<details>
<summary><b>SD-JWT</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-sdjwt-public` / `-impl` | Selective Disclosure JWT |

</details>

<details>
<summary><b>Identity Proofing</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-identity-resolution-public` / `-impl` | Identity resolution and lookup |
| `lib-identity-matching-public` / `-impl` | Probabilistic identity matching |
| `lib-identity-reconciliation-public` / `-impl` | Policy-driven reconciliation |
| `lib-identity-idv-public` / `-impl` | Identity verification orchestration |

</details>

<details>
<summary><b>Data Link / Transport</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-data-link-ble` | BLE communication layer |
| `lib-data-link-nfc` | NFC communication layer |
| `lib-data-link-http-client-public` / `-impl` | [HTTP client abstraction](https://docs.sphereon.com/idk/guides/http/http-client) |

</details>

<details>
<summary><b>Storage</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-data-store-kv-public` / `-impl` | [Key-value storage](https://docs.sphereon.com/idk/guides/data-store/key-value-store) abstraction |
| `lib-data-store-kv-impl-memory` | In-memory KV store |
| `lib-data-store-kv-impl-kottage` | Persistent KV store (Kottage) |
| `lib-data-store-blob-public` / `-impl-fs` | [Blob storage](https://docs.sphereon.com/idk/guides/data-store/blob-store) (filesystem) |
| `lib-data-store-party-public` / `-impl` | [Party / tenant data models](https://docs.sphereon.com/idk/guides/data-store/party-management) |

</details>

<details>
<summary><b>Trust</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-trust-etsi` | ETSI trust list support |
| `lib-trust-etsi-entities-public` | ETSI entities models |
| `lib-trust-x509` | X.509 certificate trust |
| `lib-trust-did` | DID-based trust |
| `lib-trust-oidfed` | OpenID Federation |

</details>

<details>
<summary><b>Credential Design</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-credential-claims-mapper-public` / `-impl` | Claim mapping for credential payloads |
| `lib-data-store-credential-design-public` / `-impl` | [Credential design](https://docs.sphereon.com/idk/guides/credential-design/overview) registry |

</details>

<details>
<summary><b>Server / Integration</b></summary>

| Module | Description |
|:-------|:------------|
| `ktor-server-kotlin-inject` | [Ktor + Metro DI integration](https://docs.sphereon.com/idk/guides/http/ktor) |

</details>

<details>
<summary><b>CBOR</b></summary>

| Module | Description |
|:-------|:------------|
| `lib-cbor-public` / `-impl` | [CBOR encoding/decoding](https://docs.sphereon.com/idk/guides/cbor) |

</details>

---

## Services

IDK ships reference services that wrap the core libraries with HTTP adapters. Each has its own page on the documentation site.

| Service | Module | Documentation |
|:--------|:-------|:--------------|
| KMS REST | `services/kms` | [docs.sphereon.com/idk/services/kms](https://docs.sphereon.com/idk/services/kms) |
| Ktor base | `services/ktor` | [docs.sphereon.com/idk/services/ktor](https://docs.sphereon.com/idk/services/ktor) |
| OAuth 2.0 Authorization Server | `services/oauth2-as/rest` | [docs.sphereon.com/idk/services/oauth2-as](https://docs.sphereon.com/idk/services/oauth2-as) |
| OID4VCI Issuer | `services/oid4vci-issuer/rest` | [docs.sphereon.com/idk/services/oid4vci-issuer](https://docs.sphereon.com/idk/services/oid4vci-issuer) |
| OID4VP Verifier | `services/oid4vp-verifier/rest` | [docs.sphereon.com/idk/services/oid4vp-verifier](https://docs.sphereon.com/idk/services/oid4vp-verifier) |

The OID4VCI Holder REST service has moved to the [Enterprise Development Kit](https://docs.sphereon.com/edk). Holder library modules (`lib-openid-oid4vci-holder-*`) remain in IDK.

Services overview: [docs.sphereon.com/idk/services/overview](https://docs.sphereon.com/idk/services/overview).

---

## Architecture

IDK separates APIs from implementations:

```
lib/<domain>/
  ├── public/     # Interfaces, data models, contracts
  └── impl/       # Implementations (swappable)
```

Architecture deep-dive: [docs.sphereon.com/idk/architecture](https://docs.sphereon.com/idk/architecture).

### Error Handling

IDK uses `IdkResult<V, E>` for explicit error handling instead of exceptions:

```kotlin
import com.sphereon.core.api.Ok
import com.sphereon.core.api.Err
import com.sphereon.core.api.error.IdkError

fun createDid(): IdkResult<String, IdkError> {
    return Ok("did:key:z6Mk...")
    // or: Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid key type"))
}

when (val result = createDid()) {
    is Ok  -> println("Created: ${result.value}")
    is Err -> println("Failed: ${result.error.message}")
}
```

### Dependency Injection

IDK uses [Metro](https://github.com/ZacSweers/metro) for compile-time DI across all platforms. Metro is a Kotlin compiler plugin and works on every Kotlin Multiplatform target the IDK ships to. See the [DI app-setup guide](https://docs.sphereon.com/idk/guides/di/app-setup) and the [scopes guide](https://docs.sphereon.com/idk/guides/di/scopes).

Scopes: `AppScope` > `UserScope` > `SessionScope`.

---

## Documentation

Full documentation: [docs.sphereon.com/idk](https://docs.sphereon.com/idk/introduction).

| Section | Link |
|:--------|:-----|
| Introduction | [docs.sphereon.com/idk/introduction](https://docs.sphereon.com/idk/introduction) |
| Getting Started | [docs.sphereon.com/idk/guides/getting-started](https://docs.sphereon.com/idk/guides/getting-started) |
| Installation | [docs.sphereon.com/idk/guides/installation](https://docs.sphereon.com/idk/guides/installation) |
| Platform Setup | [docs.sphereon.com/idk/guides/platform-setup](https://docs.sphereon.com/idk/guides/platform-setup) |
| Architecture | [docs.sphereon.com/idk/architecture](https://docs.sphereon.com/idk/architecture) |
| Module Reference | [docs.sphereon.com/idk/guides/modules](https://docs.sphereon.com/idk/guides/modules) |
| Services | [docs.sphereon.com/idk/services/overview](https://docs.sphereon.com/idk/services/overview) |
| FAQ | [docs.sphereon.com/idk/guides/faq](https://docs.sphereon.com/idk/guides/faq) |

### Generating local API docs (Dokka)

```bash
./gradlew dokkaGenerate
```

Output: `build/dokka/html/index.html`. The hosted Dokka build is also linked from the documentation site.

### Build commands

| Command | Description |
|:--------|:------------|
| `./gradlew build` | Build all modules (multiplatform) |
| `./gradlew build -Dkmp.targets=jvm` | JVM only (~5 min) |
| `./gradlew allTests` | Run all tests on configured targets |
| `./gradlew jvmTest` | Run JVM tests only |
| `./gradlew testDebugUnitTest` | Run Android unit tests |
| `./gradlew iosSimulatorArm64Test` | Run iOS simulator tests |
| `./gradlew dokkaGenerate` | Generate API documentation |
| `BUILD_XCFRAMEWORKS=true ./gradlew build` | Build with iOS XCFrameworks |

---

## Related Projects

### [Enterprise Development Kit (EDK)](https://docs.sphereon.com/edk)

Sphereon's proprietary product that extends the IDK with the layers required for production enterprise deployments. The IDK provides the identity primitives (cryptography, DIDs, verifiable credentials, SD-JWT, OID4VP, KMS, command/DI framework); the EDK adds:

- **Zero-trust authorization** via the OpenID AuthZEN specification, with Cedarling (Cedar), OPA, and any AuthZEN-compliant PDP supported. Authorization is transparent: a `PolicyCommandExtension` intercepts every command before execution.
- **Identity verification and reconciliation**: composable IDV workflows chaining OIDC, document scanning, biometric, and OTP verification; privacy-preserving identity matching with HMAC-hashed linking; policy-driven reconciliation; an auth bridge from wallet presentations to OAuth2/OIDC.
- **Microservice transport**: dual transport that makes command execution location-transparent. The same command can run in-process or be forwarded to a remote service via HTTP RPC or gRPC, controlled by configuration.
- **Cloud configuration and secrets**: cloud config providers (Azure App Configuration, REST), secret vaults (AWS Secrets Manager, Azure Key Vault, HashiCorp Vault), `${secret:vault:path}` interpolation, offline cache.
- **Audit and compliance**: structured audit logging with sensitive-data redaction, multiple output formats (JSON, CEF, OCSF), tamper evidence via hash chaining and signed checkpoints.

### [Verifiable Data Exchange (VDX)](https://sphereon.com)

The full platform on top of EDK and IDK. An enterprise identity and trust platform that unifies verifiable credentials, digital signatures, wallet-based authentication, and secure data exchange into a single deployable product, with management UIs, workflow orchestration, and operational tooling. Includes the Credential Designer, Issuer/Verifier Management consoles, wallet authentication flow, and white-label branding. Full platform documentation is in progress (Q2 2026).

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the upstream-first PR flow and review process.

Open PRs against `develop` on this repo. Maintainers review here, apply the change in the internal repo with a `Co-authored-by:` trailer, and the mirror brings the result back. Your work appears on `develop` with the original attribution.

For security issues, follow the disclosure process in [SECURITY.md](SECURITY.md). Do not open public PRs or issues for vulnerabilities.

---

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

---

<p align="center">
  <sub>Built by <a href="https://sphereon.com">Sphereon</a>. Creating Trust In A Digital World.</sub>
</p>

<p align="center">
  <a href="https://sphereon.com">Website</a> &nbsp;&bull;&nbsp;
  <a href="https://docs.sphereon.com/idk">Documentation</a> &nbsp;&bull;&nbsp;
  <a href="https://github.com/Sphereon-Opensource">GitHub</a>
</p>
