plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}
metro {
}

// Root integration modules consume owning pack publications. Explicit IDK_LOCAL_PACKS
// composites substitute these same coordinates for source development.
val idkArtifactVersion = rootProject.extra["platformVersion"].toString()

kotlin {
    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    sourceSets {
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)

                // OID4VCI common (models, serializers, Oid4vciJson)
                implementation("com.sphereon.idk:lib-openid-oid4vci-common-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-openid-oid4vci-common-impl:$idkArtifactVersion")

                // OID4VCI issuer (offer creation, metadata, credential issuance models)
                implementation("com.sphereon.idk:lib-openid-oid4vci-issuer-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-openid-oid4vci-issuer-impl:$idkArtifactVersion")
                // JsonLd validators are reached transitively through issuer-impl
                // with implementation scope; the test app graph needs them on
                // the compile classpath so Metro can discover their @Inject ctors.
                implementation("com.sphereon.idk:lib-jsonld-loader:$idkArtifactVersion")

                // OID4VCI holder (offer parsing, token exchange, credential request models)
                implementation("com.sphereon.idk:lib-openid-oid4vci-holder-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-openid-oid4vci-holder-impl:$idkArtifactVersion")

                // OID4VC common (DisplayProperties, shared VC types)
                implementation("com.sphereon.idk:lib-openid-oid4vc-common-public:$idkArtifactVersion")

                // OAuth2 common models (GrantType, TokenResponse)
                implementation("com.sphereon.idk:lib-oauth2-common-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-oauth2-common-impl:$idkArtifactVersion")

                // JWT validation registry used by the OAuth2 AS REST internal surfaces.
                implementation("com.sphereon.idk:lib-oauth2-jwt-validation-api:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-oauth2-jwt-validation-impl:$idkArtifactVersion")

                // OAuth2 AS (pre-auth code verification, token exchange)
                implementation("com.sphereon.idk:lib-oauth2-server-authorization-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-oauth2-server-authorization-impl:$idkArtifactVersion")

                // OAuth2 resource-server: ValidateAccessTokenCommand is the single entry
                // point for token-bearing endpoints (`/userinfo` etc.) and is injected by
                // UserInfoHttpEndpointCommandImpl in services-oauth2-as-rest. Both the
                // public interface and the impl-side command-descriptor multibinding must
                // be on the test classpath for the SessionScope subgraph to compose.
                implementation("com.sphereon.idk:lib-oauth2-server-resource-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-oauth2-server-resource-impl:$idkArtifactVersion")

                // OAuth2 client (holder-side token exchange)
                implementation("com.sphereon.idk:lib-oauth2-client-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-oauth2-client-impl:$idkArtifactVersion")

                // Core API
                implementation("com.sphereon.idk:lib-core-api-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-core-api-default:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-core-test:$idkArtifactVersion")

                // Crypto core (ManagedIdentifierOpts, JWS/JWE types used in command args)
                implementation("com.sphereon.idk:lib-crypto-core-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-crypto-core-impl:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-crypto-core:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-crypto-kms-provider-software:$idkArtifactVersion")
                // The managed-key selector is the bridge from the KMS provider to the
                // tenant-scoped key-reference authority. Keep it on this test composition
                // root's classpath so Metro replaces the iterating-only default binding.
                implementation("com.sphereon.idk:lib-crypto-key-persistence-impl:$idkArtifactVersion")

                // HTTP client (used by holder metadata resolution)
                implementation("com.sphereon.idk:lib-data-link-http-client-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-data-link-http-client-impl:$idkArtifactVersion")

                // KV store (in-memory, used by OAuth2 AS session storage)
                implementation("com.sphereon.idk:lib-data-store-kv-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-data-store-kv-impl:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-data-store-kv-impl-memory:$idkArtifactVersion")

                // SD-JWT (format handler dependency)
                implementation("com.sphereon.idk:lib-sdjwt-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-sdjwt-impl:$idkArtifactVersion")

                // OID4VP verifier
                implementation("com.sphereon.idk:lib-openid-oid4vp-common-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-openid-oid4vp-common-impl:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-openid-oid4vp-verifier-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-openid-oid4vp-verifier-impl:$idkArtifactVersion")
                // VCDM verification is a first-class part of the integration session graph.
                // Keep the verifier and every production Data Integrity cryptosuite visible to
                // Metro here; implementation dependencies of the REST service are not exported.
                implementation("com.sphereon.idk:lib-openid-oid4vp-verifier-vcdm-impl:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-crypto-data-integrity-proof-impl:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-crypto-data-integrity-proof-eddsa-jcs-2022:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-crypto-data-integrity-proof-eddsa-rdfc-2022:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-crypto-data-integrity-proof-ecdsa-rdfc-2019:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-openid-oid4vp-dcql:$idkArtifactVersion")

                // DCQL query store (DcqlQueryConfigurationStore, DcqlQueryResolver bindings)
                implementation("com.sphereon.idk:lib-openid-oid4vp-dcql-store-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-openid-oid4vp-dcql-store-impl:$idkArtifactVersion")

                // OID4VP holder
                implementation("com.sphereon.idk:lib-openid-oid4vp-holder-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-openid-oid4vp-holder-impl:$idkArtifactVersion")

                // OID4VP universal (shared between holder/verifier)
                implementation("com.sphereon.idk:lib-openid-oid4vp-universal-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-openid-oid4vp-universal-impl:$idkArtifactVersion")

                // OAuth2 AS REST (HTTP adapter for E2E)
                implementation("com.sphereon.idk:services-oauth2-as-rest:$idkArtifactVersion")

                // OID4VCI REST (backend credential offer API + HTTP adapters for E2E)
                implementation("com.sphereon.idk:lib-openid-oid4vci-rest-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-openid-oid4vci-rest-impl:$idkArtifactVersion")
                implementation("com.sphereon.idk:services-oid4vci-issuer-rest:$idkArtifactVersion")
                // services-oid4vci-holder-rest moved to EDK. Re-add via Maven coordinate
                // (com.sphereon.idk:services-oid4vci-holder-rest:...) only after the test
                // is moved to EDK too — referencing it from IDK creates a composite-build
                // cycle. Until then, the holder REST adapter isn't registered in this DI
                // graph; HTTP-level holder scenarios are skipped.
                // implementation("com.sphereon.idk:services-oid4vci-holder-rest:$version")

                // OID4VP verifier REST (HTTP adapters for wallet presentation E2E)
                implementation("com.sphereon.idk:services-oid4vp-verifier-rest:$idkArtifactVersion")

                // mDoc
                implementation("com.sphereon.idk:lib-mdoc-core-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-mdoc-core-impl:$idkArtifactVersion")

                // CBOR (CborParser binding)
                implementation("com.sphereon.idk:lib-cbor-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-cbor-impl:$idkArtifactVersion")

                // DID manager (DidProviderRegistry needed by SdJwtVcFormatHandler)
                implementation("com.sphereon.idk:lib-did-manager-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-did-manager-impl:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-did-methods-jwk:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-did-methods-web:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-did-resolver-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-did-resolver-impl:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-did-persistence-memory:$idkArtifactVersion")

                // Ktor client (mock engine for in-process HTTP E2E tests)
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.mock)

                // Wallet credential store implementation used by neutral interaction E2E tests
                implementation("com.sphereon.idk:lib-wallet-impl:$idkArtifactVersion")

                // Neutral wallet interaction engine and OID4VP protocol adapter
                implementation("com.sphereon.idk:lib-wallet-interaction-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-wallet-interaction-impl:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-wallet-interaction-test-fixtures:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-wallet-interaction-protocol-oid4vci:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-wallet-interaction-protocol-oid4vp:$idkArtifactVersion")
                // Exercise the production wallet interaction REST command boundary in the
                // issue/store/present proof; the test must not drive the engine directly.
                implementation("com.sphereon.idk:lib-wallet-interaction-client-rest:$idkArtifactVersion")

                // WSCA/WSCD (real Software-profile key custody + key attestation): the
                // KA-on-demand e2e leg mints a holder key and self-attests it exactly the way a
                // real OSS wallet does, rather than faking a compact JWT by hand.
                implementation("com.sphereon.idk:lib-wallet-wsca-impl:$idkArtifactVersion")
                // Final graph must see the real WSCA-backed holder proof signer contribution.
                implementation("com.sphereon.idk:lib-wallet-interaction-holder-wiring:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-wallet-wscd-software:$idkArtifactVersion")
                // The Software WSCD now requires the authoritative wallet-unit owner metadata
                // that GenerateKeyCommand indexes.  This test app is intentionally ephemeral,
                // so use the shared in-memory test authority rather than disabling that check.
                implementation("com.sphereon.idk:lib-wallet-wscd-test-fixtures:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-wallet-unit-public:$idkArtifactVersion")

                // In-memory blob backing store for BlobWalletCredentialStore
                implementation("com.sphereon.idk:lib-data-store-blob-impl:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-data-store-blob-impl-memory:$idkArtifactVersion")

                // DI
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
    }
}
