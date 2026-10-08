# Review package Task 2
BASE: 048dbc952795608ee6c7955c43e431b1533ebe4e
HEAD: d3398e6430841b75766f76d1aeabf69daf037cad

## Commits
d3398e643 Thin IDK root: drop pack includeProject graph; honor IDK_LOCAL_PACKS.


## Stat
 settings.gradle.kts | 426 +++++++++-------------------------------------------
 1 file changed, 72 insertions(+), 354 deletions(-)


## Name status
M	settings.gradle.kts


## Diff (settings.gradle.kts)
diff --git a/settings.gradle.kts b/settings.gradle.kts
index 576a0459d..a643a5432 100644
--- a/settings.gradle.kts
+++ b/settings.gradle.kts
@@ -372,362 +372,14 @@ develocity {
         termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
         termsOfUseAgree = "yes"
     }
 }
 
-// Core libraries
-includeProject("lib-cbor-public", "core/lib/cbor/public")
-includeProject("lib-cbor-impl", "core/lib/cbor/impl")
-includeProject("lib-core-api-public", "core/lib/core/api/public")
-includeProject("lib-core-compat-annotations", "core/lib/core/compat-annotations")
-includeProject("lib-core-api-default", "core/lib/core/api/default")
-includeProject("lib-core-benchmarks", "lib/core/benchmarks")
-includeProject("lib-conf-settings", "core/lib/conf/settings")
-includeProject("lib-conf-yaml", "core/lib/conf/yaml")
-
-// Theme
-includeProject("lib-conf-theme-core-public", "platform/lib/conf/theme/core/public")
-includeProject("lib-conf-theme-core-impl", "platform/lib/conf/theme/core/impl")
-includeProject("lib-conf-theme-client", "platform/lib/conf/theme/client")
-includeProject("lib-conf-theme-compose", "platform/lib/conf/theme/compose")
-includeProject("lib-conf-theme-web", "platform/lib/conf/theme/web")
-// UI Components
-includeProject("lib-ui-compose", "platform/lib/ui/compose")
-includeProject("lib-ui-compose-blob-adapter", "platform/lib/ui/compose-blob-adapter")
-includeProject("lib-core-test", "core/lib/core/test")
-includeProject("lib-data-link-http-client-public", "infra/lib/data/link/http/client/public")
-includeProject("lib-data-link-http-client-impl", "infra/lib/data/link/http/client/impl")
-includeProject("lib-data-link-http-client", "infra/lib/data/link/http/client")
-includeProject("lib-core-loggers-mobile-logger", "core/lib/core/loggers/mobile-logger")
-
-// Core Events
-includeProject("lib-core-events-public", "core/lib/core/events/public")
-includeProject("lib-core-events-impl", "core/lib/core/events/impl")
-
-// Core IDN (RFC 3492 Punycode + IDNA2008)
-includeProject("lib-core-idn-public", "core/lib/core/idn/public")
-
-// Crypto libraries
-includeProject("lib-crypto-core-public", "identity-security/lib/crypto/core/public")
-includeProject("lib-crypto-core-impl", "identity-security/lib/crypto/core/impl")
-includeProject("lib-crypto-core", "identity-security/lib/crypto/core")
-includeProject("lib-crypto-secdsa-public", "identity-security/lib/crypto/secdsa/public")
-includeProject("lib-crypto-secdsa-impl", "identity-security/lib/crypto/secdsa/impl")
-includeProject("lib-crypto-kms-provider-software", "identity-security/lib/crypto/kms/provider/software")
-includeProject("lib-crypto-kms-provider-aws", "identity-security/lib/crypto/kms/provider/aws")
-includeProject("lib-crypto-kms-provider-azure", "identity-security/lib/crypto/kms/provider/azure")
-includeProject("lib-crypto-kms-provider-mobile", "identity-security/lib/crypto/kms/provider/mobile")
-includeProject("lib-crypto-kms-rest-api", "identity-security/lib/crypto/kms/rest/api")
-includeProject("lib-crypto-kms-provider-rest", "identity-security/lib/crypto/kms/provider/rest")
-// KMS REST server moved to services/kms/rest/ — see Services section below
-
-// Crypto key persistence (tenant-aware key reference store)
-includeProject("lib-crypto-key-persistence-api", "identity-security/lib/crypto/key/persistence/api")
-includeProject("lib-crypto-key-persistence-impl", "identity-security/lib/crypto/key/persistence/impl")
-includeProject("lib-crypto-key-persistence-sqlite", "identity-security/lib/crypto/key/persistence/sqlite")
-
-// Crypto certificate persistence (tenant-aware certificate reference store)
-includeProject("lib-crypto-certificate-persistence-api", "identity-security/lib/crypto/certificate/persistence/api")
-includeProject("lib-crypto-certificate-persistence-sqlite", "identity-security/lib/crypto/certificate/persistence/sqlite")
-
-// W3C Verifiable Credentials Data Integrity 1.0
-includeProject("lib-crypto-data-integrity-proof-public", "identity-security/lib/crypto/data-integrity-proof/public")
-includeProject("lib-crypto-data-integrity-proof-impl", "identity-security/lib/crypto/data-integrity-proof/impl")
-includeProject("lib-crypto-data-integrity-proof-eddsa-jcs-2022", "identity-security/lib/crypto/data-integrity-proof/eddsa-jcs-2022")
-includeProject("lib-crypto-data-integrity-proof-eddsa-rdfc-2022", "identity-security/lib/crypto/data-integrity-proof/eddsa-rdfc-2022")
-includeProject("lib-crypto-data-integrity-proof-ecdsa-rdfc-2019", "identity-security/lib/crypto/data-integrity-proof/ecdsa-rdfc-2019")
-
-// Compression primitives (GZIP / zlib / raw DEFLATE) — status lists, JWE, etc.
-includeProject("lib-compression", "core/lib/compression")
-
-// Credential Status Lists — IETF Token Status List + W3C Bitstring Status List
-includeProject("lib-statuslist-public", "protocols/lib/statuslist/public")
-includeProject("lib-statuslist-impl", "protocols/lib/statuslist/impl")
-// Public, unauthenticated token hosting REST (serves the signed jwt/cwt; open-core, so IDK).
-// Lives on the services side alongside the other IDK REST API implementations. The business-key
-// admin management REST is EDK (:lib-statuslist-management-rest).
-includeProject("services-statuslist-rest", "protocols/services/statuslist/rest")
-
-// JSON-LD 1.1 capability (Track A: loader + validators; Track B: full processor)
-includeProject("lib-jsonld-public", "identity-security/lib/jsonld/public")
-includeProject("lib-jsonld-loader", "identity-security/lib/jsonld/loader")
-includeProject("lib-jsonld-rdf-canon", "identity-security/lib/jsonld/rdf-canon")
-includeProject("lib-jsonld-processor", "identity-security/lib/jsonld/processor")
-
-// SD-JWT libraries
-includeProject("lib-sdjwt-public", "protocols/lib/sdjwt/public")
-includeProject("lib-sdjwt-impl", "protocols/lib/sdjwt/impl")
-
-// OAuth2 Common (shared models)
-includeProject("lib-oauth2-common-public", "protocols/lib/oauth2/common/public")
-includeProject("lib-oauth2-common-impl", "protocols/lib/oauth2/common/impl")
-
-// OAuth2 Client
-includeProject("lib-oauth2-client-public", "protocols/lib/oauth2/client/public")
-includeProject("lib-oauth2-client-impl", "protocols/lib/oauth2/client/impl")
-
-// OAuth2 Authorization Server
-includeProject("lib-oauth2-server-authorization-public", "protocols/lib/oauth2/server/authorization/public")
-includeProject("lib-oauth2-server-authorization-impl", "protocols/lib/oauth2/server/authorization/impl")
-includeProject("lib-oauth2-server-resource-public", "protocols/lib/oauth2/server/resource/public")
-includeProject("lib-oauth2-server-resource-impl", "protocols/lib/oauth2/server/resource/impl")
-includeProject("lib-oauth2-server-rest", "protocols/lib/oauth2/server/rest")
-
-// OpenID OID4VC (shared VC-family types)
-includeProject("lib-openid-oid4vc-common-public", "protocols/lib/openid/oid4vc/common/public")
-includeProject("lib-openid-oid4vc-common-impl", "protocols/lib/openid/oid4vc/common/impl")
-
-// OpenID OID4VCI
-includeProject("lib-openid-oid4vci-common-public", "protocols/lib/openid/oid4vci/common/public")
-includeProject("lib-openid-oid4vci-common-impl", "protocols/lib/openid/oid4vci/common/impl")
-includeProject("lib-openid-oid4vci-issuer-public", "protocols/lib/openid/oid4vci/issuer/public")
-includeProject("lib-openid-oid4vci-issuer-impl", "protocols/lib/openid/oid4vci/issuer/impl")
-includeProject("lib-openid-oid4vci-issuer-rest", "protocols/lib/openid/oid4vci/issuer/rest")
-includeProject("lib-openid-oid4vci-holder-public", "protocols/lib/openid/oid4vci/holder/public")
-includeProject("lib-openid-oid4vci-holder-impl", "protocols/lib/openid/oid4vci/holder/impl")
-includeProject("lib-openid-oid4vci-rest-public", "protocols/lib/openid/oid4vci/rest/public")
-includeProject("lib-openid-oid4vci-rest-impl", "protocols/lib/openid/oid4vci/rest/impl")
-
-// OpenID OID4VP
-includeProject("lib-openid-oid4vp-dcql", "protocols/lib/openid/oid4vp/dcql")
-includeProject("lib-openid-oid4vp-dcql-store-public", "protocols/lib/openid/oid4vp/dcql-store/public")
-includeProject("lib-openid-oid4vp-dcql-store-impl", "protocols/lib/openid/oid4vp/dcql-store/impl")
-includeProject("lib-openid-oid4vp-dcql-store-rest", "protocols/lib/openid/oid4vp/dcql-store/rest")
-includeProject("lib-openid-oid4vp-common-public", "protocols/lib/openid/oid4vp/common/public")
-includeProject("lib-openid-oid4vp-common-impl", "protocols/lib/openid/oid4vp/common/impl")
-includeProject("lib-openid-oid4vp-holder-public", "protocols/lib/openid/oid4vp/holder/public")
-includeProject("lib-openid-oid4vp-holder-impl", "protocols/lib/openid/oid4vp/holder/impl")
-includeProject("lib-openid-oid4vp-verifier-public", "protocols/lib/openid/oid4vp/verifier/public")
-includeProject("lib-openid-oid4vp-verifier-vcdm-impl", "protocols/lib/openid/oid4vp/verifier/vcdm-impl")
-includeProject("lib-openid-oid4vp-verifier-impl", "protocols/lib/openid/oid4vp/verifier/impl")
-includeProject("lib-openid-oid4vp-verifier-rest", "protocols/lib/openid/oid4vp/verifier/rest")
-includeProject("lib-openid-oid4vp-universal-public", "protocols/lib/openid/oid4vp/universal/public")
-includeProject("lib-openid-oid4vp-universal-impl", "protocols/lib/openid/oid4vp/universal/impl")
-
-// Wallet SDK
-includeProject("lib-wallet-public", "wallet-lib/lib/wallet/public")
-includeProject("lib-wallet-impl", "wallet-lib/lib/wallet/impl")
-includeProject("lib-wallet-unit-public", "wallet-lib/lib/wallet/unit/public")
-includeProject("lib-wallet-unit-impl", "wallet-lib/lib/wallet/unit/impl")
-includeProject("lib-wallet-wsca-public", "wallet-lib/lib/wallet/wsca/public")
-includeProject("lib-wallet-wsca-impl", "wallet-lib/lib/wallet/wsca/impl")
-includeProject("lib-wallet-wscd-public", "wallet-lib/lib/wallet/wscd/public")
-includeProject("lib-wallet-wscd-software", "wallet-lib/lib/wallet/wscd/software")
-includeProject("lib-wallet-wscd-mobile", "wallet-lib/lib/wallet/wscd/mobile")
-includeProject("lib-wallet-wscd-test-fixtures", "wallet-lib/lib/wallet/wscd/test-fixtures")
-includeProject("lib-wallet-provider-public", "wallet-lib/lib/wallet/provider/public")
-includeProject("lib-wallet-provider-local", "wallet-lib/lib/wallet/provider/local")
-includeProject("lib-wallet-party-public", "wallet-lib/lib/wallet/party/public")
-includeProject("lib-wallet-party-local", "wallet-lib/lib/wallet/party/local")
-
-// Wallet Interaction API (protocol-neutral headless wallet runtime)
-includeProject("lib-wallet-interaction-public", "wallet-lib/lib/wallet/interaction/public")
-includeProject("lib-wallet-interaction-impl", "wallet-lib/lib/wallet/interaction/impl")
-includeProject("lib-wallet-interaction-test-fixtures", "wallet-lib/lib/wallet/interaction/test-fixtures")
-includeProject("lib-wallet-interaction-client-rest", "wallet-lib/lib/wallet/interaction/client-rest")
-includeProject("lib-wallet-interaction-presenter", "wallet-lib/lib/wallet/interaction/presenter")
-includeProject("lib-wallet-interaction-presenter-contracts", "wallet-lib/lib/wallet/interaction/presenter-contracts")
-includeProject("lib-wallet-interaction-protocol-oid4vci", "wallet-lib/lib/wallet/interaction/protocol/oid4vci")
-includeProject("lib-wallet-interaction-protocol-oid4vp", "wallet-lib/lib/wallet/interaction/protocol/oid4vp")
-includeProject("lib-wallet-interaction-protocol-iso18013", "wallet-lib/lib/wallet/interaction/protocol/iso18013")
-includeProject("lib-wallet-interaction-holder-wiring", "wallet-lib/lib/wallet/interaction/holder-wiring")
-
-includeProject("wallet-profile-public", "wallet/profile/public")
-includeProject("wallet-profile-impl", "wallet/profile/impl")
-includeProject("wallet-app-public", "wallet/app/public") // Phase 1
-includeProject("wallet-app-impl", "wallet/app/impl")
-includeProject("wallet-app-client-rest", "wallet/app/client-rest")
-includeProject("wallet-kit", "wallet/kit")
-includeProject("wallet-presentation-contracts", "wallet/presentation/contracts")
-includeProject("wallet-presentation", "wallet/presentation/presenter")
-includeProject("wallet-presentation-molecule", "wallet/presentation/molecule")
-includeProject("wallet-ui-compose", "wallet/ui/compose")
-includeProject("wallet-ui-navigation3", "wallet/ui/navigation3")
-includeProject("wallet-reference-app", "wallet/reference/app")
-includeProject("wallet-reference-compose", "wallet/reference/compose")
-includeProject("wallet-reference-local", "wallet/reference/local")
-includeProject("wallet-reference-node", "wallet/reference/node")
-includeProject("wallet-reference-wasm", "wallet/reference/wasm")
-includeProject("wallet-cli", "wallet/cli")
-includeProject("wallet-runner", "wallet/runner")
-includeProject("wallet-example-custom-wscd", "wallet/examples/custom-wscd")
-
-// Data Link - BLE
-includeProject("lib-data-link-ble-public", "infra/lib/data/link/ble/public")
-includeProject("lib-data-link-ble-test-fixtures", "infra/lib/data/link/ble/test-fixtures")
-includeProject("lib-data-link-ble-robots", "infra/lib/data/link/ble/robots")
-
-// Data Link - NFC
-includeProject("lib-data-link-nfc-impl", "infra/lib/data/link/nfc/impl")
-includeProject("lib-data-link-nfc-public", "infra/lib/data/link/nfc/public")
-
-// Data Store (cross-cutting storage abstractions)
-includeProject("lib-data-store-kv-public", "infra/lib/data/store/kv/public")
-includeProject("lib-data-store-kv-impl", "infra/lib/data/store/kv/impl")
-includeProject("lib-data-store-kv-impl-memory", "infra/lib/data/store/kv/impl-memory")
-includeProject("lib-data-store-kv-impl-kottage", "infra/lib/data/store/kv/impl-kottage")
-includeProject("lib-data-store-kv-impl-android-protected", "infra/lib/data/store/kv/impl-android-protected")
-
-// Data Store - Blob (cross-cutting blob/object storage abstraction)
-includeProject("lib-data-store-blob-public", "infra/lib/data/store/blob/public")
-includeProject("lib-data-store-blob-impl", "infra/lib/data/store/blob/impl")
-
-// Data Store - Vault (provider-neutral protected file/folder contract)
-includeProject("lib-data-store-vault-public", "infra/lib/data/store/vault/public")
-includeProject("lib-data-store-vault-portability", "infra/lib/data/store/vault/portability")
-
-// Data Integration (cross-cutting transport/resource/operation taxonomy for connectors,
-// inventory, workflows, forms, and policy).
-includeProject("lib-data-integration-public", "infra/lib/data/integration/public")
-
-// Attribute Flow (flow-agnostic attribute wiring primitives: AttributeBag, AttributePath,
-// AttributeSource/Target/Binding. Consumed by IDV graphs, issuance pipelines, tabular sources, etc.)
-includeProject("lib-attribute-flow-public", "platform/lib/attribute/flow/public")
-
-// Attribute Mapping (generic source -> target attribute rename rules + applier; reused by
-// reconciliation flows, CSV-roster issuance, OIDC claim projection, etc.)
-includeProject("lib-attribute-mapping-public", "platform/lib/attribute/mapping/public")
-
-
-// Invitation service — RELOCATED to VDX as vdx-service-invitation-* per
-// feedback_edk_vs_vdx_placement (invitation orchestration is a product feature,
-// not an open-source primitive).
-includeProject("lib-data-store-blob-impl-memory", "infra/lib/data/store/blob/impl-memory")
-includeProject("lib-data-store-blob-impl-fs", "infra/lib/data/store/blob/impl-fs")
-includeProject("lib-data-store-blob-impl-kv", "infra/lib/data/store/blob/impl-kv")
-includeProject("lib-data-store-blob-client-http", "infra/lib/data/store/blob/client-http")
-
-// Data Store - Asset (tenant asset library: content-addressed, per-tenant deduplicated
-// assets over the blob store; shared by theming/branding and credential design)
-includeProject("lib-data-store-asset-public", "infra/lib/data/store/asset/public")
-includeProject("lib-data-store-asset-impl", "infra/lib/data/store/asset/impl")
-
-// Data Store - OKD (Onderwijs Koppeling voor Document Management — Dutch MBO education standard)
-includeProject("lib-data-store-okd-openapi", "infra/lib/data/store/okd-openapi")
-includeProject("lib-data-store-blob-impl-okd", "infra/lib/data/store/blob/impl-okd")
-includeProject("lib-data-store-okd-server", "infra/lib/data/store/okd-server")
-
-// Data Store - Schema Registry (schema management with blob store backing)
-includeProject("lib-data-store-schema-registry-public", "infra/lib/data/store/schema-registry/public")
-includeProject("lib-data-store-schema-registry-impl", "infra/lib/data/store/schema-registry/impl")
-
-// Data Store - Credential Design (design, localization, and render metadata)
-includeProject("lib-data-store-credential-design-public", "infra/lib/data/store/credential-design/public")
-includeProject("lib-data-store-credential-design-impl", "infra/lib/data/store/credential-design/impl")
-
-// Data Store - Credential Type Binding (role-independent registry: semantic attribute set -> credential wire format identity)
-includeProject("lib-data-store-credential-type-binding-public", "infra/lib/data/store/credential-type-binding/public")
-includeProject("lib-data-store-credential-type-binding-impl", "infra/lib/data/store/credential-type-binding/impl")
-
-// Data - Credential Definition (role-neutral free-form/lightweight definition: pure claim data, no profile link)
-includeProject("lib-data-credential-definition-public", "platform/lib/data/credential-definition/public")
-includeProject("lib-data-credential-definition-impl", "platform/lib/data/credential-definition/impl")
-includeProject("lib-data-credential-definition-rest", "platform/lib/data/credential-definition/rest")
-
-// Software Registry (unified software-instance model + read/write SPIs)
-includeProject("lib-software-registry-public", "identity-security/lib/software/registry/public")
-includeProject("lib-software-registry-impl", "identity-security/lib/software/registry/impl")
-
-// Data Store - Party (data models for identity, contact, tenant)
-includeProject("lib-data-store-party-public", "infra/lib/data/store/party/public")
-
-// DID libraries (W3C Decentralized Identifiers)
-includeProject("lib-did-core-public", "identity-security/lib/did/core/public")
-includeProject("lib-did-resolver-public", "identity-security/lib/did/resolver/public")
-includeProject("lib-did-resolver-impl", "identity-security/lib/did/resolver/impl")
-includeProject("lib-did-manager-public", "identity-security/lib/did/manager/public")
-includeProject("lib-did-manager-impl", "identity-security/lib/did/manager/impl")
-includeProject("lib-did-methods-key", "identity-security/lib/did/methods/key")
-includeProject("lib-did-methods-jwk", "identity-security/lib/did/methods/jwk")
-includeProject("lib-did-methods-web", "identity-security/lib/did/methods/web")
-includeProject("lib-did-methods-webvh-public", "identity-security/lib/did/methods/webvh/public")
-includeProject("lib-did-methods-webvh-resolver", "identity-security/lib/did/methods/webvh/resolver")
-includeProject("lib-did-methods-webvh-provider", "identity-security/lib/did/methods/webvh/provider")
-includeProject("lib-did-methods-webvh-rest-server", "identity-security/lib/did/methods/webvh/rest/server")
-includeProject("lib-did-persistence-api", "identity-security/lib/did/persistence/api")
-includeProject("lib-did-persistence-memory", "identity-security/lib/did/persistence/memory")
-includeProject("lib-did-persistence-sqlite", "identity-security/lib/did/persistence/sqlite")
-includeProject("lib-did-persistence-test-fixtures", "identity-security/lib/did/persistence/test-fixtures")
-includeProject("lib-did-rest-resolver-server", "identity-security/lib/did/rest/resolver/server")
-includeProject("lib-did-hosting-public", "identity-security/lib/did/hosting/public")
-includeProject("lib-did-hosting-impl", "identity-security/lib/did/hosting/impl")
-
-// mDoc libraries
-includeProject("lib-mdoc-core-public", "protocols/lib/mdoc/core/public")
-includeProject("lib-mdoc-core-impl", "protocols/lib/mdoc/core/impl")
-includeProject("lib-mdoc-core", "protocols/lib/mdoc/core")
-includeProject("lib-mdoc-transport-ble-public", "protocols/lib/mdoc/transport-ble/public")
-includeProject("lib-mdoc-transport-ble-impl", "protocols/lib/mdoc/transport-ble/impl")
-includeProject("lib-mdoc-transport-ble", "protocols/lib/mdoc/transport-ble")
-includeProject("lib-mdoc-transport-nfc", "protocols/lib/mdoc/transport-nfc")
-includeProject("lib-mdoc-transport-restapi", "protocols/lib/mdoc/transport-restapi")
-includeProject("lib-mdoc-transport-oid4vp", "protocols/lib/mdoc/transport-oid4vp")
-includeProject("lib-mdoc-datatransfer-public", "protocols/lib/mdoc/datatransfer/public")
-includeProject("lib-mdoc-datatransfer-impl", "protocols/lib/mdoc/datatransfer/impl")
-includeProject("lib-mdoc-datatransfer", "protocols/lib/mdoc/datatransfer")
-includeProject("lib-mdoc-reader", "protocols/lib/mdoc/reader")
-
-
-
-// TS 11 attestation catalogs
-includeProject("lib-catalog-public", "platform/lib/catalog/public")
-includeProject("lib-catalog-ts11-public", "platform/lib/catalog/ts11-public")
-includeProject("lib-catalog-impl", "platform/lib/catalog/impl")
-includeProject("lib-catalog-persistence-api", "platform/lib/catalog/persistence/api")
-includeProject("lib-catalog-persistence-memory", "platform/lib/catalog/persistence/memory")
-includeProject("lib-catalog-persistence-sqlite", "platform/lib/catalog/persistence/sqlite")
-
-// Trust libraries
-includeProject("lib-trust-core-public", "identity-security/lib/trust/core/public")
-includeProject("lib-trust-core-impl", "identity-security/lib/trust/core/impl")
-includeProject("lib-trust-etsi-entities-public", "identity-security/lib/trust/etsi-entities-public")
-includeProject("lib-trust-etsi", "identity-security/lib/trust/etsi")
-includeProject("lib-trust-x509", "identity-security/lib/trust/x509")
-includeProject("lib-trust-did", "identity-security/lib/trust/did")
-includeProject("lib-trust-oidfed", "identity-security/lib/trust/oidfed")
-
-
-
-// OAuth2 JWT Validation
-includeProject("lib-oauth2-jwt-validation-api", "protocols/lib/oauth2/jwt/validation/api")
-includeProject("lib-oauth2-jwt-validation-impl", "protocols/lib/oauth2/jwt/validation/impl")
-
-// Credential Claims Mapper
-includeProject("lib-credential-claims-mapper-public", "identity-security/lib/credential/claims-mapper/public")
-includeProject("lib-credential-claims-mapper-impl", "identity-security/lib/credential/claims-mapper/impl")
-
-// OID4VP Authentication Bridge
-includeProject("lib-openid-oid4vp-auth-bridge-public", "protocols/lib/openid/oid4vp/auth-bridge/public")
-includeProject("lib-openid-oid4vp-auth-bridge-impl", "protocols/lib/openid/oid4vp/auth-bridge/impl")
-
-// Identity Matching
-includeProject("lib-identity-matching-public", "identity-security/lib/identity/matching/public")
-includeProject("lib-identity-matching-impl", "identity-security/lib/identity/matching/impl")
-
-// Identity Resolution
-includeProject("lib-identity-resolution-public", "identity-security/lib/identity/resolution/public")
-includeProject("lib-identity-resolution-impl", "identity-security/lib/identity/resolution/impl")
-
-// Identity Reconciliation
-includeProject("lib-identity-reconciliation-public", "identity-security/lib/identity/reconciliation/public")
-includeProject("lib-identity-reconciliation-impl", "identity-security/lib/identity/reconciliation/impl")
-
-// Identity Verification
-includeProject("lib-idv-public", "identity-security/lib/identity/idv/public")
-includeProject("lib-idv-oidc", "identity-security/lib/identity/idv/oidc")
-includeProject("lib-idv-wallet", "identity-security/lib/identity/idv/wallet")
-
-// Services (REST API deployment modules)
-includeProject("ktor-server-kotlin-inject", "infra/services/ktor/server/plugins/ktor-server-kotlin-inject")
-includeProject("ktor-server-jwt-auth", "infra/services/ktor/server/plugins/ktor-server-jwt-auth")
-includeProject("services-kms-rest", "identity-security/services/kms/rest")
-includeProject("services-did-manager-rest", "identity-security/services/did/manager/rest")
-includeProject("services-did-hosting-rest", "identity-security/services/did/hosting/rest")
-includeProject("services-oid4vp-verifier-rest", "protocols/services/oid4vp-verifier/rest")
-includeProject("services-oauth2-as-rest", "protocols/services/oauth2-as/rest")
-includeProject("services-oid4vci-issuer-rest", "protocols/services/oid4vci-issuer/rest")
-// services-oid4vci-holder-rest moved to EDK (vdx/edk/services/oid4vci-holder/rest)
+// Pack modules (core/, platform/, infra/, identity-security/, protocols/, wallet-lib/, wallet/)
+// are owned by pack settings via includeBuild when IDK_LOCAL_PACKS is set — not registered
+// as projects of Identity-Development-Kit. lib-core-benchmarks removed: core/lib/core/benchmarks
+// does not exist (orphan path was lib/core/benchmarks).
 
 // Examples
 includeProject("examples-oid4vc-webapp-server", "examples/oid4vc/webapp/server")
 includeProject("examples-service-byo-oidc", "examples/service-byo-oidc")
 
@@ -770,7 +422,73 @@ if (useLibAllBuild) {
     includeProject("lib-all", "lib/all")
 } else {
     println("==> lib-all (XCFramework): DISABLED - Set BUILD_XCFRAMEWORKS=true to enable")
 }
 
-// BOM
-includeProject("idk-bom", "platform/versions/idk-bom")
+// keep in sync with gradle/idk-local-packs.gradle.kts
+// (apply(from) does not export top-level functions into this settings script)
+val IDK_PACK_DIRS: Set<String> =
+    setOf("core", "platform", "infra", "identity-security", "protocols", "wallet-lib")
+
+fun parseIdkLocalPacks(): List<String> {
+    val raw = System.getenv("IDK_LOCAL_PACKS")?.trim().orEmpty()
+    if (raw.isEmpty()) return emptyList()
+    return raw.split(",")
+        .map { it.trim() }
+        .filter { it.isNotEmpty() }
+        .also { packs ->
+            packs.forEach { pack ->
+                if (pack !in IDK_PACK_DIRS) {
+                    throw GradleException(
+                        "Unknown IDK_LOCAL_PACKS entry '$pack'. Allowed: ${IDK_PACK_DIRS.joinToString()}",
+                    )
+                }
+            }
+        }
+        .distinct()
+}
+
+fun extractIdkPackModuleNames(settingsFile: File): List<String> {
+    if (!settingsFile.isFile) return emptyList()
+    val localOrMapped =
+        Regex("""include(?:Local|Mapped)\s*\(\s*"([^"]+)"\s*,\s*"[^"]+"\s*\)""")
+    return settingsFile.readLines()
+        .filter { line ->
+            val t = line.trim()
+            !t.startsWith("//") && !t.startsWith("/*") && !t.startsWith("*")
+        }
+        .mapNotNull { line -> localOrMapped.find(line)?.groupValues?.get(1) }
+        .distinct()
+}
+
+fun Settings.includeIdkLocalPackBuilds(idkRoot: File) {
+    val packs = parseIdkLocalPacks()
+    if (packs.isEmpty()) {
+        println("==> IDK pack source builds: none (IDK_LOCAL_PACKS empty; using Maven artifacts for IDK modules)")
+        return
+    }
+    packs.forEach { pack ->
+        val packRoot = idkRoot.resolve(pack)
+        val packSettings = packRoot.resolve("settings.gradle.kts")
+        if (!packSettings.isFile) {
+            throw GradleException("Missing pack settings: ${packSettings.absolutePath}")
+        }
+        val modules = extractIdkPackModuleNames(packSettings)
+        if (modules.isEmpty()) {
+            throw GradleException(
+                "No includeLocal/includeMapped modules in ${packSettings.absolutePath}",
+            )
+        }
+        includeBuild(packRoot) {
+            name = "idk-$pack"
+            dependencySubstitution {
+                modules.forEach { moduleName ->
+                    substitute(module("com.sphereon.idk:$moduleName")).using(project(":$moduleName"))
+                }
+            }
+        }
+        println("==> IDK pack source build: $pack (${modules.size} modules)")
+    }
+}
+
+// Selective multi-pack source composite when developing from the IDK checkout root.
+includeIdkLocalPackBuilds(settings.rootDir)

