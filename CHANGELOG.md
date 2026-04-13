# Changelog

## 0.13.1 — 2026-01-13

### Improvements

- Refactored mDoc data transfer layer with improved session establishment handling
- NFC engagement stability improvements on Android
- Clarified engagement URI schemes: `mdoc:` for classic reverse engagement (ISO 18013-5), `mdoc://` for website retrieval (ISO 18013-7)

---

## 0.13.0 — 2026-01-07

### Breaking Changes

#### IdkResult API Overhaul
The `IdkResult` type has been completely redesigned for better iOS/Swift/ObjC interoperability:
- **Changed from typealias to wrapper class**: `IdkResult` is now a proper class wrapping kotlin-result's `Result`, instead of a simple typealias
- **New `Ok` and `Err` subclasses**: Use `Ok(value)` and `Err(error)` constructors instead of kotlin-result's functions directly
- **Import changes**: Import `com.sphereon.core.api.Ok` and `com.sphereon.core.api.Err` (top-level functions)
- **New methods**: `component1()`, `component2()` for destructuring; `getOrThrow()` for exception-based handling
- **No more need to depend on com.github.michaelbull.result**

**Migration example:**
```kotlin
// Old (0.10)
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Err
return Ok(value)

// New (0.13)
import com.sphereon.core.api.Ok
import com.sphereon.core.api.Err
return Ok(value)
```

#### Module Splits
- **CBOR**: Split into `lib/cbor/public` and `lib/cbor/impl`
- **HTTP Client**: Split into `lib/data/link/http/client/public` and `impl`

### New Modules

#### DID Support (`lib/did`)
- `lib/did/core` - Core DID functionality
- `lib/did/manager` - DID management
- `lib/did/methods` - DID method implementations
- `lib/did/persistence` - DID persistence layer
- `lib/did/resolver` - DID resolution
- `lib/did/rest` - REST API for DID operations

#### OAuth2 (`lib/oauth2`)
- `lib/oauth2/common` - Shared OAuth2 types and utilities
- `lib/oauth2/client` - OAuth2 client implementation
- `lib/oauth2/server` - OAuth2 authorization server support
- `lib/oauth2/jwt` - JWT handling for OAuth2

#### OpenID4VP (`lib/openid/oid4vp`)
- Universal OID4VP support for Verifiable Presentations
- Integration with mDoc transport layer

#### SD-JWT (`lib/sdjwt`)
- `lib/sdjwt/public` - SD-JWT interfaces and types
- `lib/sdjwt/impl` - SD-JWT implementation
- Selective Disclosure JWT support

#### Configuration (`lib/conf/settings`)
- Multiplatform configuration/settings support

#### Event System (`lib/core/events`)
- `lib/core/events/public` - Event interfaces
- `lib/core/events/impl` - Event system implementation

#### Party/Tenant Storage (`lib/data/store/party`)
- Party and tenant persistence support
- Correlation identifier management

### Features

#### Cryptographic Enhancements
- **JWS Support**: JSON Web Signature creation and verification
- **JWE Support**: JSON Web Encryption support
- **JWT Validation**: Comprehensive JWT validation

#### Universal HTTP Adapter System
- New endpoint command pattern for HTTP adapters
- Configurable HTTP client factory
- Support for modular REST API deployment

#### Configurable KV Storage
- Pluggable key-value storage abstraction

### Platform Improvements

#### iOS/Apple
- BLE peripheral server fixes and improvements
- ECDH key exchange fixes
- Key management improvements
- Ktor support for iOS platforms
- ObjC interop improvements for IdkResult

#### BLE Transport
- Peripheral server stability improvements
- Transport layer decoupling refactor
- Improved timing and concurrency handling
- Write without notify for peripheral mode


### Ktor Support
- Ktor server kotlin-inject plugin
- Ktor support modules for server development


---

## 0.10.0 — 2025-11-05
First public 0.X release

### Highlights
- Multiplatform targets: Android, iOS (iosX64, iosArm64, iosSimulatorArm64)
- Android library publishing with debug and release variants

### Features
- Core APIs: Public and default Core API modules exposed via commonMain
- Data link: 
  - BLE support for Android and iOS
  - NFC support for Android
- Crypto:
    - Core crypto and KMS common modules
    - KMS Providers:
      - Mobile Key Management (iOS)
      - Android Keystore (Android)
      - AWS KMS
      - Azure KeyVault and HSM
      - Software Keystore (ephermal keys)
    - Cryptographic primitives:
      - RSA
      - ECDSA
- CBOR/MDOC:
    - CBOR library inclusion
    - mDoc core integration for mobile document workflows
    - Data transfer: BLE and NFC data link public libraries integrated
- Dependency Injection:
    - Kotlin Inject / Amazon LastMile DI stack with KSP-based code generation
    - Gradle helper to add KSP processors across Android and iOS targets
- Concurrency & logging:
    - kotlinx-coroutines (core, test, android)


### Android
- minSdk 27, compileSdk 35 (extension 15), Java 17 toolchain
- Instrumented testing with Espresso, JUnit, Turbine, MockK
- Packaging exclusion to avoid META-INF conflicts

### Build/Tooling
- Kotlin Multiplatform with default hierarchy template
- KSP across Android and iOS targets; Anvil/Kotlin Inject processors enabled
- Publication configured for Android variants
- ProGuard configs for debug/release (minify disabled)

### Testing
- Common and Android unit tests with Kotest, Coroutines Test, Turbine
- Android instrumented tests with Espresso and test rules
- iOS targets prepared for KMP testing

### Notes
- Classic KSP used (KSP2 disabled on some projects, because of a bug)

### Migration
- When upgrading from snapshots, perform a clean build to ensure DI code generation for all targets
