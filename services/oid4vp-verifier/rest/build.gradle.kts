plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    id("com.sphereon.gradle.plugin.service-deployable")
}
metro {
}
serviceDeployable {
    mainClass.set("com.sphereon.openid.oid4vp.verifier.ktor.Oid4vpVerifierKtorServerKt")
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
        val commonMain by getting {
            dependencies {
                // Reusable OID4VP REST adapters (verifier + universal) — the adapter classes
                // and endpoint commands this standalone server mounts. `api` so the server's
                // jvmMain and the test source sets see them.
                api(projects.libOpenidOid4vpVerifierRest)

                // OID4VP Verifier + Universal service logic
                implementation(projects.libOpenidOid4vpVerifierPublic)
                implementation(projects.libOpenidOid4vpVerifierImpl)

                // Credential status verification: brings the StatusListResolver binding + the two
                // CredentialStatusVerifier set members so the verifier actually checks status lists.
                implementation(projects.libStatuslistPublic)
                implementation(projects.libStatuslistImpl)
                implementation(projects.libOpenidOid4vpUniversalPublic)
                implementation(projects.libOpenidOid4vpUniversalImpl)
                implementation(projects.libOpenidOid4vpCommonPublic)
                implementation(projects.libOpenidOid4vpDcql)
                // DCQL store: KvDcqlQueryConfigurationStore binding (required by THIS
                // standalone server's own graph) + admin ServiceCommands + REST surface. A
                // consuming assembly that brings its own DCQL store depends on
                // lib-openid-oid4vp-verifier-rest directly instead of this module.
                implementation(projects.libOpenidOid4vpDcqlStoreImpl)
                implementation(projects.libOpenidOid4vpDcqlStoreRest)
                // JsonLd validators are reached transitively through verifier-impl
                // with implementation scope; the Ktor server graph needs them on
                // the compile classpath so Metro can discover their @Inject ctors.
                implementation(projects.libJsonldLoader)

                // Core
                implementation(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCore)
                implementation(projects.libCryptoCorePublic)

                // KV storage (for session stores)
                implementation(projects.libDataStoreKvPublic)
                implementation(projects.libDataStoreKvImpl)
                implementation(projects.libDataStoreKvImplMemory)

                // SD-JWT
                implementation(projects.libSdjwtPublic)

                // HTTP client (for callbacks)
                implementation(projects.libDataLinkHttpClientPublic)

                // DI (Metro)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)

                // Serialization
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                // Ktor client (for HTTP operations in commands)
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCoreTest)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libOauth2CommonImpl)
                implementation(projects.libOauth2ClientImpl)
                implementation(projects.libOpenidOid4vpHolderImpl)
                implementation(projects.libSdjwtImpl)
                implementation(sphereonlib.io.ktor.client.mock)
            }
        }

        val jvmMain by getting {
            dependencies {
                // Ktor server
                implementation(projects.ktorServerKotlinInject)
                // YAML config (must be direct dep for Metro to discover YamlFileAppPropertySourceImpl)
                implementation(projects.libConfYaml)
                implementation(sphereonlib.io.ktor.server.core)
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.server.content.negotiation)
                implementation(sphereonlib.io.ktor.server.status.pages)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)

                // Crypto providers for runtime
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libDataLinkHttpClientImpl)

                // DID manager (for did:jwk kid resolution in JAR signing)
                implementation(projects.libDidManagerPublic)
                implementation(projects.libDidManagerImpl)
                implementation(projects.libDidPersistenceApi)
                implementation(projects.libDidPersistenceMemory)
                implementation(projects.libDidResolverImpl)
                implementation(projects.libDidMethodsJwk)
                implementation(projects.libDidMethodsKey)

                // OAuth2 (needed for JAR support in verifier)
                implementation(projects.libOauth2CommonImpl)
                implementation(projects.libOauth2ClientImpl)

                // SD-JWT (runtime impl for verification bindings)
                implementation(projects.libSdjwtImpl)

                // mDoc verification bindings used by VerifyHolderBindingCommandImpl
                // (MdocValidations, DeviceAuthValidation, DeviceResponseCborCodec).
                // libMdocCoreImpl pulls libCborImpl + COSE bindings transitively.
                implementation(projects.libMdocCoreImpl)
                implementation(projects.libCborImpl)

                // Trust framework: X509TrustAnchorLoaderImpl feeds the mdoc IACA path
                // (and X509TrustValidationService); DefaultTrustConfigProvider binds
                // `trust.*` config keys.
                implementation(projects.libTrustX509)
                implementation(projects.libTrustCoreImpl)

                // Events
                implementation(projects.libCoreEventsImpl)
            }
        }
    }
}
