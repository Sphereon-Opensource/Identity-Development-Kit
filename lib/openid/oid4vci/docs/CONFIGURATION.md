# OID4VCI Configuration Guide

This document covers all configuration options for the OID4VCI issuer and client modules.

## Overview

Configuration follows three tiers:

| Tier | Interface | Scope | Purpose |
|---|---|---|---|
| **Issuer config** | `Oid4vciIssuerConfigProvider` | Per issuer | Metadata, credential configurations, signing key |
| **Issuance policy** | `CredentialIssuancePolicyConfig` | Per credential configuration | IAE requirements, allowed grants, nonce TTL, encryption, deferred retry |
| **Client config** | `Oid4vciClientConfig` | Per client/wallet | Client ID, nonce behavior, polling settings |

All properties use the IDK `ConfigService` with hierarchical resolution (App → Tenant → Principal). Properties can be set via YAML, environment variables, or the settings store.

---

## Issuer Configuration

### `Oid4vciIssuerConfigProvider`

Supplies issuer metadata for the `/.well-known/openid-credential-issuer` endpoint.

| Property | Type | Required | Description |
|---|---|---|---|
| `issuerIdentifier` | `String` | Yes | The issuer's identifier URL (must be HTTPS, or `http://localhost` for development) |
| `credentialConfigurations` | `Map<String, CredentialConfigurationSupported>` | Yes | Supported credential configurations keyed by `credential_configuration_id` |
| `authorizationServers` | `List<String>?` | No | OAuth 2.0 Authorization Server identifiers. If omitted, the issuer URL is used as the AS. |
| `display` | `List<DisplayProperties>?` | No | Issuer display properties (name, locale, logo) for wallet UIs |
| `signingKey` | `ManagedIdentifierOptsOrResult?` | No | Key for signing issuer metadata (OID4VCI 1.1 `signed_metadata` field) |

#### Signed Metadata Behavior

When `signingKey` is configured:
- `GET /.well-known/openid-credential-issuer` with `Accept: application/jwt` returns a signed JWT
- JSON responses include the `signed_metadata` field containing the signed JWT
- The JWT uses `typ: openidvci-issuer-metadata+jwt`

When `signingKey` is null:
- JWT requests receive HTTP 406 Not Acceptable
- JSON responses omit `signed_metadata`

---

## Config-Driven Issuer Metadata

`ConfigDrivenOid4vciIssuerConfigProvider` reads issuer metadata and credential configurations
directly from ConfigService properties. This is the simplest deployment option — no credential
design store is needed. Credential configuration IDs are declared explicitly as a comma-separated
list so there is no ambiguity about which credentials are active.

### Root namespace

```
sphereon.oid4vci.issuer.*
```

### Issuer-level properties

| Property key | Type | Required | Description |
|---|---|---|---|
| `sphereon.oid4vci.issuer.identifier` | String | Yes | Issuer identifier URL (used as `credential_issuer` in metadata) |
| `sphereon.oid4vci.issuer.authorizationServers` | String (comma-separated) | No | OAuth 2.0 Authorization Server URLs. Omit to use the issuer URL as the AS. |
| `sphereon.oid4vci.issuer.credentialConfigurationIds` | String (comma-separated) | Yes | IDs of credential configurations to expose. Each ID must have a corresponding `credentials.<id>.*` block. |
| `sphereon.oid4vci.issuer.display.name` | String | No | Human-readable issuer name shown in wallet UIs |
| `sphereon.oid4vci.issuer.display.locale` | String | No | BCP47 locale for the display name (e.g. `en-US`) |

### Per-credential configuration properties

Each credential configuration ID listed in `credentialConfigurationIds` is configured under:

```
sphereon.oid4vci.issuer.credentials.<credentialConfigurationId>.*
```

| Property key (relative) | Type | Default | Description |
|---|---|---|---|
| `format` | String | `dc+sd-jwt` | OID4VCI credential format: `dc+sd-jwt`, `vc+sd-jwt`, `jwt_vc_json`, `mso_mdoc` |
| `scope` | String | _(none)_ | OAuth2 scope string for this credential configuration |
| `vct` | String | _(none)_ | Verifiable Credential Type URI (SD-JWT DC / vc+sd-jwt formats) |
| `doctype` | String | _(none)_ | Document type (mso_mdoc format) |
| `signingAlgorithms` | String (comma-separated) | _(none)_ | Signing algorithms for credential signing, e.g. `ES256,ES384` |
| `bindingMethods` | String (comma-separated) | _(none)_ | Cryptographic binding methods, e.g. `jwk,did:key,did:jwk` |
| `proofTypes.jwt.signingAlgorithms` | String (comma-separated) | _(none)_ | Signing algorithms accepted in JWT proofs, e.g. `ES256` |
| `credentialDefinition.types` | String (comma-separated) | _(none)_ | W3C VC type array (jwt_vc_json format), e.g. `VerifiableCredential,UniversityDegreeCredential` |
| `display.name` | String | _(none)_ | Human-readable credential name shown in wallet UIs |
| `display.locale` | String | _(none)_ | BCP47 locale for the credential display name |

### Configuration examples

#### YAML

```yaml
sphereon:
  oid4vci:
    issuer:
      identifier: https://issuer.example.com
      authorizationServers: https://as.example.com
      credentialConfigurationIds: UniversityDegree,MembershipCard
      display:
        name: Example University
        locale: en-US
      credentials:
        UniversityDegree:
          format: jwt_vc_json
          scope: degree
          signingAlgorithms: ES256,ES384
          bindingMethods: jwk,did:key
          proofTypes:
            jwt:
              signingAlgorithms: ES256
          credentialDefinition:
            types: VerifiableCredential,UniversityDegreeCredential
          display:
            name: University Degree
            locale: en-US
        MembershipCard:
          format: dc+sd-jwt
          scope: membership
          vct: https://credentials.example.com/membership
          signingAlgorithms: ES256
          bindingMethods: jwk
          proofTypes:
            jwt:
              signingAlgorithms: ES256
          display:
            name: Membership Card
            locale: en-US
```

#### Environment variables

```bash
SPHEREON_OID4VCI_ISSUER_IDENTIFIER=https://issuer.example.com
SPHEREON_OID4VCI_ISSUER_AUTHORIZATIONSERVERS=https://as.example.com
SPHEREON_OID4VCI_ISSUER_CREDENTIALCONFIGURATIONIDS=UniversityDegree,MembershipCard
SPHEREON_OID4VCI_ISSUER_DISPLAY_NAME=Example University
SPHEREON_OID4VCI_ISSUER_DISPLAY_LOCALE=en-US

# UniversityDegree credential configuration
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_FORMAT=jwt_vc_json
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_SCOPE=degree
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_SIGNINGALGORITHMS=ES256,ES384
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_BINDINGMETHODS=jwk,did:key
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_PROOFTYPES_JWT_SIGNINGALGORITHMS=ES256
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_CREDENTIALDEFINITION_TYPES=VerifiableCredential,UniversityDegreeCredential
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_DISPLAY_NAME=University Degree
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_DISPLAY_LOCALE=en-US

# MembershipCard credential configuration
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_MEMBERSHIPCARD_FORMAT=dc+sd-jwt
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_MEMBERSHIPCARD_SCOPE=membership
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_MEMBERSHIPCARD_VCT=https://credentials.example.com/membership
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_MEMBERSHIPCARD_SIGNINGALGORITHMS=ES256
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_MEMBERSHIPCARD_BINDINGMETHODS=jwk
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_MEMBERSHIPCARD_PROOFTYPES_JWT_SIGNINGALGORITHMS=ES256
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_MEMBERSHIPCARD_DISPLAY_NAME=Membership Card
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_MEMBERSHIPCARD_DISPLAY_LOCALE=en-US
```

### Deployment wiring

`ConfigDrivenOid4vciIssuerConfigProvider` is NOT annotated with `@ContributesBinding`. The deployment
module must explicitly wire it as the active `Oid4vciIssuerConfigProvider`:

```kotlin
@ContributesTo(AppScope::class)
interface MyIssuerModule {
    @Provides
    fun provideIssuerConfigProvider(
        impl: ConfigDrivenOid4vciIssuerConfigProvider,
    ): Oid4vciIssuerConfigProvider = impl
}
```

This is the same pattern used by `DesignBackedOid4vciIssuerConfigProvider`, giving deployment modules
explicit control over which provider is active.

### Relationship to per-credential issuance policy

The properties under `sphereon.oid4vci.issuer.credentials.<id>.*` serve two distinct purposes:

- **Metadata properties** (format, scope, vct, doctype, signing algorithms, binding methods, display)
  are read by `ConfigDrivenOid4vciIssuerConfigProvider` to build the `/.well-known/openid-credential-issuer`
  response.
- **Policy properties** (iae, grants, nonce, deferred, encryption) are read by
  `DefaultCredentialIssuancePolicyResolver` at issuance time to enforce access control.

Both use the same `sphereon.oid4vci.issuer.credentials.<id>` namespace prefix, so all properties
for a credential are grouped together in configuration.

---

## Per-Credential Issuance Policy

### Config namespace

```
sphereon.oid4vci.issuer.credentials.<credentialConfigurationId>.*
```

Each credential configuration ID can have its own policy. When no policy is configured, safe defaults apply (IAE disabled, all grants allowed).

### Interactive Authorization (IAE)

Controls whether the wallet must perform additional interaction (VP presentation, web-based auth) before credential issuance.

| Property | Type | Default | Description |
|---|---|---|---|
| `iae.enabled` | Boolean | `false` | Whether IAE is required for this credential. When `false`, standard auth code / pre-auth flows are used. |
| `iae.interaction-type` | String | `urn:openid:dcp:iae:openid4vp_presentation` | The interaction type the AS requests. Options: `urn:openid:dcp:iae:openid4vp_presentation` (present a credential), `urn:openid:dcp:iae:redirect_to_web` (browser-based authentication). |
| `iae.dcql-query-id` | String | _(none)_ | ID of the DCQL query configuration to use for VP presentation. References a `DcqlQueryConfiguration` by its `queryId`. When not set, a minimal query is used. |

### Grant Types

Controls which OAuth 2.0 grant types are allowed in credential offers.

| Property | Type | Default | Description |
|---|---|---|---|
| `grants.pre-authorized-code.allowed` | Boolean | `true` | Whether the `urn:ietf:params:oauth:grant-type:pre-authorized_code` grant is permitted. |
| `grants.pre-authorized-code.tx-code-required` | Boolean | `false` | Whether a transaction code (PIN) is required for the pre-authorized code grant. The issuer generates the code and communicates it out-of-band (e.g., email). |
| `grants.authorization-code.allowed` | Boolean | `true` | Whether the `authorization_code` grant is permitted. |

### Nonce

| Property | Type | Default | Description |
|---|---|---|---|
| `nonce.ttl-seconds` | Long | `300` | Lifetime of `c_nonce` values in seconds. The wallet must use the nonce within this window. |

### Deferred Issuance

| Property | Type | Default | Description |
|---|---|---|---|
| `deferred.retry-interval-seconds` | Int | `5` | The `interval` value returned in deferred credential responses, telling the wallet how long to wait between polling attempts (in seconds). |

### Credential Response Encryption

| Property | Type | Default | Description |
|---|---|---|---|
| `encryption.response-required` | Boolean | `false` | Whether the wallet must include `credential_response_encryption` in the credential request. When `true`, unencrypted requests are rejected. |

### Configuration Examples

#### YAML

```yaml
sphereon:
  oid4vci:
    issuer:
      credentials:
        # University degree — requires presenting a national ID via IAE
        UniversityDegreeCredential:
          iae:
            enabled: true
            interaction-type: urn:openid:dcp:iae:openid4vp_presentation
            dcql-query-id: national-id-presentation
          grants:
            pre-authorized-code:
              allowed: true
              tx-code-required: true
            authorization-code:
              allowed: true
          nonce:
            ttl-seconds: 600
          deferred:
            retry-interval-seconds: 10

        # Membership card — simple pre-auth flow, no IAE
        MembershipCard:
          iae:
            enabled: false
          grants:
            pre-authorized-code:
              allowed: true
            authorization-code:
              allowed: false

        # Government PID — web-based identity verification
        GovernmentPID:
          iae:
            enabled: true
            interaction-type: urn:openid:dcp:iae:redirect_to_web
          grants:
            pre-authorized-code:
              allowed: false
            authorization-code:
              allowed: true
          encryption:
            response-required: true
```

#### Environment Variables

```bash
# University degree with IAE
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_IAE_ENABLED=true
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_IAE_INTERACTION_TYPE=urn:openid:dcp:iae:openid4vp_presentation
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_IAE_DCQL_QUERY_ID=national-id-presentation
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_GRANTS_PRE_AUTHORIZED_CODE_ALLOWED=true
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_GRANTS_PRE_AUTHORIZED_CODE_TX_CODE_REQUIRED=true

# Membership card — no IAE, pre-auth only
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_MEMBERSHIPCARD_IAE_ENABLED=false
SPHEREON_OID4VCI_ISSUER_CREDENTIALS_MEMBERSHIPCARD_GRANTS_AUTHORIZATION_CODE_ALLOWED=false
```

### How Policy Is Evaluated

**At offer creation** (`CreateCredentialOfferCommand`):
- For each `credentialConfigurationId` in the offer, the policy is resolved
- If the requested grant type is not allowed by policy, the offer creation fails with an error
- `txCodeRequired` determines whether a `tx_code` object is included in the offer

**At IAE request** (`HandleIaeInitialRequestCommand`):
- The `credential_configuration_id` is extracted from `authorization_details`
- Policy determines whether IAE is needed and which interaction type to use
- The `dcqlQueryId` selects which DCQL query to send in the VP presentation request

**At credential request** (`HandleCredentialRequestCommand`):
- Policy can be used to enforce additional access control (assurance level, required claims)

---

## Client Configuration

### `Oid4vciClientConfig`

Controls wallet/client behavior during credential issuance flows.

| Property | Type | Default | Description |
|---|---|---|---|
| `clientId` | `String?` | _(none)_ | OAuth 2.0 client identifier. Null for anonymous pre-authorized code flows. |
| `preferredFormat` | `String?` | _(none)_ | Preferred credential format when the issuer supports multiple (`dc+sd-jwt`, `mso_mdoc`, `jwt_vc_json`). |
| `autoRequestNonce` | `Boolean` | `true` | When `true` and the issuer has a `nonce_endpoint`, the orchestrator automatically fetches a nonce before credential requests. |
| `defaultDeferredPollingInterval` | `Int` | `5` | Default polling interval in seconds for deferred credential retrieval, when the server doesn't specify one. |
| `maxDeferredPollingAttempts` | `Int` | `60` | Maximum number of in-process polling attempts before the `PollDeferredCredentialCommand` returns `Exhausted`. |

### Client Polling Behavior

The `PollDeferredCredentialCommand` runs an in-process loop:
1. Waits `interval` seconds (server-specified or `defaultDeferredPollingInterval`)
2. Polls the deferred credential endpoint
3. If credential ready → returns `Ready` result
4. If still pending → server may update `interval` and `transaction_id`
5. After `maxDeferredPollingAttempts` → returns `Exhausted` result (not an error)

For long-lived deferred issuance (hours/days), EDK can call the command from a persistent background job with a higher `maxAttempts`.

---

## Spec References

| Config area | OID4VCI spec section |
|---|---|
| Issuer metadata | 1.1 Section 13.2 |
| `signed_metadata` | 1.1 Section 13.2 (JWT field) |
| Credential offer grants | 1.1 Section 4 |
| Transaction code (`tx_code`) | 1.1 Section 4 |
| IAE interaction types | 1.1 Section 6 |
| Nonce endpoint | 1.1 Section 8 |
| Deferred credential | 1.1 Section 10 |
| Credential response encryption | 1.1 Section 11 |
