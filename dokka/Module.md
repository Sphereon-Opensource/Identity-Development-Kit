# Module Identity-Development-Kit (IDK)

The Identity Development Kit (IDK) is Sphereon's open-core Kotlin Multiplatform SDK for building identity, credential, and trust solutions. It provides the foundational command, configuration, DI, crypto, DID, credential, and transport primitives that the commercial EDK and VDX platforms build on.

## Module naming

IDK uses a consistent three-way split per domain area:

- `lib-<domain>-<area>-public` exposes interfaces, data classes, and command bindings. Safe to depend on from API surfaces.
- `lib-<domain>-<area>-impl` supplies concrete, DI-wired implementations contributed into Metro dependency graphs (`dev.zacsweers.metro`) via `@ContributesBinding`, `@ContributesTo`, and `@Inject`, scoped with `AppScope`, `UserScope`, or `SessionScope`.

`services-*` modules are deployable Ktor REST servers. `examples-*` and `tests-*` are reference and integration code.

## Modules by domain area

### Core

- `lib-core-api-public`: result monad, command hierarchy, configuration, logging, HTTP adapter, caching, and auth contracts shared by the entire SDK.
- `lib-core-api-default`: default DI-wired runtime for `lib-core-api-public`.
- `lib-core-events-public` / `lib-core-events-impl`: typed application event hub.
- `lib-core-test`: test fixtures for consumers of the core API.
- `lib-core-benchmarks`: JMH benchmarks for core hot paths.
- `lib-core-loggers-mobile-logger`: on-device `LogService` implementation for mobile apps.
- `lib-cbor-public` / `lib-cbor-impl`: CBOR data model and runtime (RFC 8949).

### Configuration and Theme

- `lib-conf-settings`: `multiplatform-settings`-backed property source.
- `lib-conf-yaml`: YAML property sources for app, tenant, and principal scopes.
- `lib-conf-theme-core-public` / `lib-conf-theme-core-impl`: branding and theme metadata model and runtime.
- `lib-conf-theme-compose`: Compose Multiplatform bindings for IDK theming.
- `lib-conf-theme-web`: CSS token bindings for web UIs.
- `lib-ui-compose`: shared Compose component token primitives.

### Crypto and KMS

- `lib-crypto-core-public` / `lib-crypto-core-impl`: COSE and JOSE crypto primitives, algorithms, and codecs.
- `lib-crypto-kms-provider-software`: in-process software KMS.
- `lib-crypto-kms-provider-aws`: AWS KMS provider.
- `lib-crypto-kms-provider-azure`: Azure Key Vault provider.
- `lib-crypto-kms-provider-mobile`: iOS Secure Enclave / Android Keystore provider.
- `lib-crypto-kms-provider-rest`: remote KMS provider using `services-kms-rest`.
- `lib-crypto-key-persistence-api` / `lib-crypto-key-persistence-impl` / `lib-crypto-key-persistence-sqlite`: tenant-aware key reference store (pointers, not key material).

### DID

- `lib-did-core-public`: DID method capability model.
- `lib-did-resolver-public` / `lib-did-resolver-impl`: resolver registry and dereferencing.
- `lib-did-manager-public` / `lib-did-manager-impl`: DID lifecycle management.
- `lib-did-methods-key`, `lib-did-methods-jwk`, `lib-did-methods-web`: per-method providers and resolvers.
- `lib-did-persistence-api` / `lib-did-persistence-memory` / `lib-did-persistence-sqlite`: DID record persistence.
- `lib-did-rest-resolver-server`: Universal Resolver HTTP front-end.

### SD-JWT

- `lib-sdjwt-public` / `lib-sdjwt-impl`: IETF SD-JWT issuer, holder, and verifier commands.

### OAuth2 and OIDC

- `lib-oauth2-common-public` / `lib-oauth2-common-impl`: shared client-authentication, DPoP, id_token, introspection, and revocation.
- `lib-oauth2-client-public` / `lib-oauth2-client-impl`: OAuth2 client with JAR, PAR, and token exchange.
- `lib-oauth2-server-authorization-public` / `lib-oauth2-server-authorization-impl`: Authorization Server command graph.
- `lib-oauth2-server-resource-public` / `lib-oauth2-server-resource-impl`: Resource Server validation, introspection, DPoP caches.
- `lib-oauth2-jwt-validation-api` / `lib-oauth2-jwt-validation-impl`: JWT and OIDC discovery validation.

### OpenID4VCI

- `lib-openid-oid4vc-common-public` / `lib-openid-oid4vc-common-impl`: shared OID4VC types, QR code service.
- `lib-openid-oid4vci-common-public` / `lib-openid-oid4vci-common-impl`: OID4VCI model and validators.
- `lib-openid-oid4vci-issuer-public` / `lib-openid-oid4vci-issuer-impl`: issuer runtime.
- `lib-openid-oid4vci-holder-public` / `lib-openid-oid4vci-holder-impl`: holder (wallet) runtime.
- `lib-openid-oid4vci-rest-public` / `lib-openid-oid4vci-rest-impl`: REST-facing issuer service layer.

### OpenID4VP

- `lib-openid-oid4vp-dcql`: DCQL query and response types.
- `lib-openid-oid4vp-common-public` / `lib-openid-oid4vp-common-impl`: shared OID4VP request types.
- `lib-openid-oid4vp-holder-public` / `lib-openid-oid4vp-holder-impl`: holder runtime.
- `lib-openid-oid4vp-verifier-public` / `lib-openid-oid4vp-verifier-impl`: verifier runtime.
- `lib-openid-oid4vp-universal-public` / `lib-openid-oid4vp-universal-impl`: universal verifier service layer.
- `lib-openid-oid4vp-auth-bridge-public` / `lib-openid-oid4vp-auth-bridge-impl`: OID4VP-to-authentication bridge.

### mDoc (ISO 18013)

- `lib-mdoc-core-public` / `lib-mdoc-core-impl` / `lib-mdoc-core`: ISO 18013-5 document model and CBOR codecs.
- `lib-mdoc-transport-ble-public` / `lib-mdoc-transport-ble-impl` / `lib-mdoc-transport-ble`: BLE device retrieval.
- `lib-mdoc-transport-nfc`: NFC engagement and handover.
- `lib-mdoc-transport-restapi`: REST API transport.
- `lib-mdoc-transport-oid4vp`: ISO 18013-7 online flow over OID4VP.
- `lib-mdoc-datatransfer-public` / `lib-mdoc-datatransfer-impl` / `lib-mdoc-datatransfer`: device engagement orchestration.
- `lib-mdoc-reader`: mdoc reader runtime.

### Data Link (transports)

- `lib-data-link-http-client-public` / `lib-data-link-http-client-impl`: HTTP client contracts and commands.
- `lib-data-link-ble-public`, `lib-data-link-ble-test-fixtures`, `lib-data-link-ble-robots`: BLE primitives and test helpers.
- `lib-data-link-nfc-public` / `lib-data-link-nfc-impl`: NFC APDU and NDEF primitives.

### Data Store

- `lib-data-store-kv-public` / `lib-data-store-kv-impl` / `lib-data-store-kv-impl-memory` / `lib-data-store-kv-impl-kottage`: key-value store contracts and backings.
- `lib-data-store-blob-public` / `lib-data-store-blob-impl` / `lib-data-store-blob-impl-memory` / `lib-data-store-blob-impl-fs` / `lib-data-store-blob-impl-kv` / `lib-data-store-blob-client-http`: blob / object store contracts and backings.
- `lib-data-store-okd-openapi` / `lib-data-store-blob-impl-okd` / `lib-data-store-okd-server`: OKD (Dutch MBO) document store integration.
- `lib-data-store-schema-registry-public` / `lib-data-store-schema-registry-impl`: versioned schema registry on top of a blob store.
- `lib-data-store-credential-design-public` / `lib-data-store-credential-design-impl`: credential design, localization, and render metadata.
- `lib-data-store-party-public`: shared party / identity / tenant filter and pagination model.

### Trust

- `lib-trust-core-public` / `lib-trust-core-impl`: entity discovery, trust-anchor refresh, revocation.
- `lib-trust-etsi-entities-public` / `lib-trust-etsi`: ETSI LOTL trust list support.
- `lib-trust-x509`: X.509 trust validation.
- `lib-trust-did`: DID-based trust validation.
- `lib-trust-oidfed`: OpenID Federation trust validation.

### Identity

- `lib-identity-matching-public` / `lib-identity-matching-impl`: hashed / encrypted identifier matching.
- `lib-identity-resolution-public` / `lib-identity-resolution-impl`: identity resolution.
- `lib-identity-reconciliation-public` / `lib-identity-reconciliation-impl`: cross-source attribute reconciliation.
- `lib-idv-public`, `lib-idv-oidc`, `lib-idv-wallet`: identity verification API and drivers (OIDC IDP, wallet-based).

### Credential

- `lib-credential-claims-mapper-public` / `lib-credential-claims-mapper-impl`: credential claim projection (for example DCQL or id_token claims).

### Services (REST)

- `ktor-server-kotlin-inject`: Ktor plugin that bridges Metro dependency graphs into request handling (the artifact name is retained from the pre-Metro codebase for compatibility).
- `services-kms-rest`: deployable KMS REST server.
- `services-oid4vp-verifier-rest`: deployable OID4VP verifier server.
- `services-oauth2-as-rest`: deployable OAuth2/OIDC Authorization Server.
- `services-oid4vci-issuer-rest`: deployable OID4VCI issuer server.

### Examples and versions

- `examples-oid4vc-webapp-server`: reference OID4VC webapp demonstrating issuance and verification.
- `idk-bom`: Bill of Materials aligning versions across IDK modules.

## Platforms

IDK supports JVM, JavaScript, Wasm for browser and Node.js, iOS, and Linux targets.

## Getting Started

Add IDK dependencies to your project via Maven / Gradle from Sphereon's repository.

For more information, visit [docs.sphereon.com](https://docs.sphereon.com).
