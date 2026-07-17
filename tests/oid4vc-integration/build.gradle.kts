plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}
metro {
}

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
                implementation(projects.libOpenidOid4vciCommonPublic)
                implementation(projects.libOpenidOid4vciCommonImpl)

                // OID4VCI issuer (offer creation, metadata, credential issuance models)
                implementation(projects.libOpenidOid4vciIssuerPublic)
                implementation(projects.libOpenidOid4vciIssuerImpl)
                // JsonLd validators are reached transitively through issuer-impl
                // with implementation scope; the test app graph needs them on
                // the compile classpath so Metro can discover their @Inject ctors.
                implementation(projects.libJsonldLoader)

                // OID4VCI holder (offer parsing, token exchange, credential request models)
                implementation(projects.libOpenidOid4vciHolderPublic)
                implementation(projects.libOpenidOid4vciHolderImpl)

                // OID4VC common (DisplayProperties, shared VC types)
                implementation(projects.libOpenidOid4vcCommonPublic)

                // OAuth2 common models (GrantType, TokenResponse)
                implementation(projects.libOauth2CommonPublic)
                implementation(projects.libOauth2CommonImpl)

                // JWT validation registry used by OAuth2 AS REST signing-key provisioning.
                implementation(projects.libOauth2JwtValidationApi)
                implementation(projects.libOauth2JwtValidationImpl)

                // OAuth2 AS (pre-auth code verification, token exchange)
                implementation(projects.libOauth2ServerAuthorizationPublic)
                implementation(projects.libOauth2ServerAuthorizationImpl)

                // OAuth2 resource-server: ValidateAccessTokenCommand is the single entry
                // point for token-bearing endpoints (`/userinfo` etc.) and is injected by
                // UserInfoHttpEndpointCommandImpl in services-oauth2-as-rest. Both the
                // public interface and the impl-side command-descriptor multibinding must
                // be on the test classpath for the SessionScope subgraph to compose.
                implementation(projects.libOauth2ServerResourcePublic)
                implementation(projects.libOauth2ServerResourceImpl)

                // OAuth2 client (holder-side token exchange)
                implementation(projects.libOauth2ClientPublic)
                implementation(projects.libOauth2ClientImpl)

                // Core API
                implementation(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCoreTest)

                // Crypto core (ManagedIdentifierOpts, JWS/JWE types used in command args)
                implementation(projects.libCryptoCorePublic)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoCore)
                implementation(projects.libCryptoKmsProviderSoftware)

                // HTTP client (used by holder metadata resolution)
                implementation(projects.libDataLinkHttpClientPublic)
                implementation(projects.libDataLinkHttpClientImpl)

                // KV store (in-memory, used by OAuth2 AS session storage)
                implementation(projects.libDataStoreKvPublic)
                implementation(projects.libDataStoreKvImpl)
                implementation(projects.libDataStoreKvImplMemory)

                // SD-JWT (format handler dependency)
                implementation(projects.libSdjwtPublic)
                implementation(projects.libSdjwtImpl)

                // OID4VP verifier
                implementation(projects.libOpenidOid4vpCommonPublic)
                implementation(projects.libOpenidOid4vpCommonImpl)
                implementation(projects.libOpenidOid4vpVerifierPublic)
                implementation(projects.libOpenidOid4vpVerifierImpl)
                implementation(projects.libOpenidOid4vpDcql)

                // DCQL query store (DcqlQueryConfigurationStore, DcqlQueryResolver bindings)
                implementation(projects.libOpenidOid4vpDcqlStorePublic)
                implementation(projects.libOpenidOid4vpDcqlStoreImpl)

                // OID4VP holder
                implementation(projects.libOpenidOid4vpHolderPublic)
                implementation(projects.libOpenidOid4vpHolderImpl)

                // OID4VP universal (shared between holder/verifier)
                implementation(projects.libOpenidOid4vpUniversalPublic)
                implementation(projects.libOpenidOid4vpUniversalImpl)

                // OAuth2 AS REST (HTTP adapter for E2E)
                implementation(projects.servicesOauth2AsRest)

                // OID4VCI REST (backend credential offer API + HTTP adapters for E2E)
                implementation(projects.libOpenidOid4vciRestPublic)
                implementation(projects.libOpenidOid4vciRestImpl)
                implementation(projects.servicesOid4vciIssuerRest)
                // services-oid4vci-holder-rest moved to EDK. Re-add via Maven coordinate
                // (com.sphereon.idk:services-oid4vci-holder-rest:...) only after the test
                // is moved to EDK too — referencing it from IDK creates a composite-build
                // cycle. Until then, the holder REST adapter isn't registered in this DI
                // graph; HTTP-level holder scenarios are skipped.
                // implementation("com.sphereon.idk:services-oid4vci-holder-rest:0.25.0-SNAPSHOT")

                // OID4VP verifier REST (HTTP adapters for wallet presentation E2E)
                implementation(projects.servicesOid4vpVerifierRest)

                // mDoc
                implementation(projects.libMdocCorePublic)
                implementation(projects.libMdocCoreImpl)

                // CBOR (CborParser binding)
                implementation(projects.libCborPublic)
                implementation(projects.libCborImpl)

                // DID manager (DidProviderRegistry needed by SdJwtDcFormatHandler)
                implementation(projects.libDidManagerPublic)
                implementation(projects.libDidManagerImpl)
                implementation(projects.libDidMethodsJwk)
                implementation(projects.libDidMethodsWeb)
                implementation(projects.libDidResolverPublic)
                implementation(projects.libDidResolverImpl)
                implementation(projects.libDidPersistenceMemory)

                // Ktor client (mock engine for in-process HTTP E2E tests)
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.mock)

                // Wallet credential store implementation used by neutral interaction E2E tests
                implementation(projects.libWalletImpl)

                // Neutral wallet interaction engine and OID4VP protocol adapter
                implementation(projects.libWalletInteractionPublic)
                implementation(projects.libWalletInteractionImpl)
                implementation(projects.libWalletInteractionProtocolOid4vci)
                implementation(projects.libWalletInteractionProtocolOid4vp)

                // WSCA/WSCD (real Software-profile key custody + key attestation): the
                // KA-on-demand e2e leg mints a holder key and self-attests it exactly the way a
                // real OSS wallet does, rather than faking a compact JWT by hand.
                implementation(projects.libWalletWscaImpl)
                implementation(projects.libWalletWscdSoftware)
                implementation(projects.libWalletUnitPublic)

                // In-memory blob backing store for BlobWalletCredentialStore
                implementation(projects.libDataStoreBlobImpl)
                implementation(projects.libDataStoreBlobImplMemory)

                // DI
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
    }
}
