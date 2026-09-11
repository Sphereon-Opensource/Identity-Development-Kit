plugins {
    id("com.sphereon.gradle.toml-catalog")
}

// No cross-layer imports — IDK is the base layer
dependencies {
    constraints {
        // Core libraries
        api("com.sphereon.idk:lib-cbor-public:$version")
        api("com.sphereon.idk:lib-cbor-impl:$version")
        api("com.sphereon.idk:lib-core-api-public:$version")
        api("com.sphereon.idk:lib-core-api-default:$version")
        api("com.sphereon.idk:lib-core-benchmarks:$version")
        api("com.sphereon.idk:lib-conf-settings:$version")

        // Theme
        api("com.sphereon.idk:lib-conf-theme-core-public:$version")
        api("com.sphereon.idk:lib-conf-theme-core-impl:$version")
        api("com.sphereon.idk:lib-conf-theme-compose:$version")
        api("com.sphereon.idk:lib-core-test:$version")
        api("com.sphereon.idk:lib-data-link-http-client-public:$version")
        api("com.sphereon.idk:lib-data-link-http-client-impl:$version")
        api("com.sphereon.idk:lib-data-link-http-client:$version")
        api("com.sphereon.idk:lib-core-loggers-mobile-logger:$version")

        // Core Events
        api("com.sphereon.idk:lib-core-events-public:$version")
        api("com.sphereon.idk:lib-core-events-impl:$version")

        // Crypto libraries
        api("com.sphereon.idk:lib-crypto-core-public:$version")
        api("com.sphereon.idk:lib-crypto-core-impl:$version")
        api("com.sphereon.idk:lib-crypto-core:$version")
        api("com.sphereon.idk:lib-crypto-kms-provider-software:$version")
        api("com.sphereon.idk:lib-crypto-kms-provider-aws:$version")
        api("com.sphereon.idk:lib-crypto-kms-provider-azure:$version")
        api("com.sphereon.idk:lib-crypto-kms-provider-mobile:$version")
        api("com.sphereon.idk:lib-crypto-kms-rest-api:$version")

        // SD-JWT libraries
        api("com.sphereon.idk:lib-sdjwt-public:$version")
        api("com.sphereon.idk:lib-sdjwt-impl:$version")

        // OAuth2 Common
        api("com.sphereon.idk:lib-oauth2-common-public:$version")
        api("com.sphereon.idk:lib-oauth2-common-impl:$version")

        // OAuth2 Client
        api("com.sphereon.idk:lib-oauth2-client-public:$version")
        api("com.sphereon.idk:lib-oauth2-client-impl:$version")

        // OAuth2 Authorization Server
        api("com.sphereon.idk:lib-oauth2-server-authorization-public:$version")
        api("com.sphereon.idk:lib-oauth2-server-authorization-impl:$version")
        api("com.sphereon.idk:lib-oauth2-server-resource-public:$version")
        api("com.sphereon.idk:lib-oauth2-server-resource-impl:$version")

        // OpenID OID4VC (shared)
        api("com.sphereon.idk:lib-openid-oid4vc-common-public:$version")
        api("com.sphereon.idk:lib-openid-oid4vc-common-impl:$version")

        // OpenID OID4VCI
        api("com.sphereon.idk:lib-openid-oid4vci-common-public:$version")
        api("com.sphereon.idk:lib-openid-oid4vci-common-impl:$version")
        api("com.sphereon.idk:lib-openid-oid4vci-issuer-public:$version")
        api("com.sphereon.idk:lib-openid-oid4vci-issuer-impl:$version")
        api("com.sphereon.idk:lib-openid-oid4vci-issuer-rest:$version")

        // OpenID OID4VP
        api("com.sphereon.idk:lib-openid-oid4vp-dcql:$version")
        api("com.sphereon.idk:lib-openid-oid4vp-common-public:$version")
        api("com.sphereon.idk:lib-openid-oid4vp-common-impl:$version")
        api("com.sphereon.idk:lib-openid-oid4vp-holder-public:$version")
        api("com.sphereon.idk:lib-openid-oid4vp-holder-impl:$version")
        api("com.sphereon.idk:lib-openid-oid4vp-verifier-public:$version")
        api("com.sphereon.idk:lib-openid-oid4vp-verifier-impl:$version")
        api("com.sphereon.idk:lib-openid-oid4vp-verifier-rest:$version")
        api("com.sphereon.idk:lib-openid-oid4vp-universal-public:$version")
        api("com.sphereon.idk:lib-openid-oid4vp-universal-impl:$version")

        // Data Link - BLE
        api("com.sphereon.idk:lib-data-link-ble-public:$version")
        api("com.sphereon.idk:lib-data-link-ble-test-fixtures:$version")
        api("com.sphereon.idk:lib-data-link-ble-robots:$version")

        // Data Link - NFC
        api("com.sphereon.idk:lib-data-link-nfc-impl:$version")
        api("com.sphereon.idk:lib-data-link-nfc-public:$version")

        // Data Store - KV
        api("com.sphereon.idk:lib-data-store-kv-public:$version")
        api("com.sphereon.idk:lib-data-store-kv-impl:$version")
        api("com.sphereon.idk:lib-data-store-kv-impl-memory:$version")
        api("com.sphereon.idk:lib-data-store-kv-impl-kottage:$version")

        // Data Store - Party
        api("com.sphereon.idk:lib-data-store-party-public:$version")

        // Data Integration
        api("com.sphereon.idk:lib-data-integration-public:$version")

        // DID libraries
        api("com.sphereon.idk:lib-did-core-public:$version")
        api("com.sphereon.idk:lib-did-resolver-public:$version")
        api("com.sphereon.idk:lib-did-resolver-impl:$version")
        api("com.sphereon.idk:lib-did-manager-public:$version")
        api("com.sphereon.idk:lib-did-manager-impl:$version")
        api("com.sphereon.idk:lib-did-methods-key:$version")
        api("com.sphereon.idk:lib-did-methods-jwk:$version")
        api("com.sphereon.idk:lib-did-methods-web:$version")
        api("com.sphereon.idk:lib-did-persistence-api:$version")
        api("com.sphereon.idk:lib-did-persistence-memory:$version")
        api("com.sphereon.idk:lib-did-persistence-sqlite:$version")
        api("com.sphereon.idk:lib-did-rest-resolver-server:$version")

        // mDoc libraries
        api("com.sphereon.idk:lib-mdoc-core-public:$version")
        api("com.sphereon.idk:lib-mdoc-core-impl:$version")
        api("com.sphereon.idk:lib-mdoc-core:$version")
        api("com.sphereon.idk:lib-mdoc-transport-ble-public:$version")
        api("com.sphereon.idk:lib-mdoc-transport-ble-impl:$version")
        api("com.sphereon.idk:lib-mdoc-transport-ble:$version")
        api("com.sphereon.idk:lib-mdoc-transport-nfc:$version")
        api("com.sphereon.idk:lib-mdoc-transport-restapi:$version")
        api("com.sphereon.idk:lib-mdoc-transport-oid4vp:$version")
        api("com.sphereon.idk:lib-mdoc-datatransfer-public:$version")
        api("com.sphereon.idk:lib-mdoc-datatransfer-impl:$version")
        api("com.sphereon.idk:lib-mdoc-datatransfer:$version")
        api("com.sphereon.idk:lib-mdoc-reader:$version")

        // Attestation catalogs
        api("com.sphereon.idk:lib-catalog-public:$version")
        api("com.sphereon.idk:lib-catalog-impl:$version")
        api("com.sphereon.idk:lib-catalog-persistence-api:$version")
        api("com.sphereon.idk:lib-catalog-persistence-memory:$version")
        api("com.sphereon.idk:lib-catalog-persistence-sqlite:$version")

        // Trust libraries
        api("com.sphereon.idk:lib-trust-core-public:$version")
        api("com.sphereon.idk:lib-trust-core-impl:$version")
        api("com.sphereon.idk:lib-trust-etsi-entities-public:$version")
        api("com.sphereon.idk:lib-trust-etsi:$version")
        api("com.sphereon.idk:lib-trust-x509:$version")
        api("com.sphereon.idk:lib-trust-did:$version")
        api("com.sphereon.idk:lib-trust-oidfed:$version")

        // OAuth2 JWT Validation
        api("com.sphereon.idk:lib-oauth2-jwt-validation-api:$version")
        api("com.sphereon.idk:lib-oauth2-jwt-validation-impl:$version")

        // Ktor support
        api("com.sphereon.idk:ktor-server-kotlin-inject:$version")

        // Services (REST API deployment modules)
        api("com.sphereon.idk:services-kms-rest:$version")
        api("com.sphereon.idk:services-oid4vp-verifier-rest:$version")
        api("com.sphereon.idk:services-oauth2-as-rest:$version")
        api("com.sphereon.idk:services-oid4vci-issuer-rest:$version")
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(tasks.named("generateTomlCatalog"))
}
tasks.withType<PublishToMavenLocal>().configureEach {
    dependsOn(tasks.named("generateTomlCatalog"))
}
