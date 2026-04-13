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
    mainClass.set("com.sphereon.openid.oid4vci.issuer.ktor.Oid4vciIssuerKtorServerKt")
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
                // OID4VCI REST API (backend credential offer management)
                implementation(projects.libOpenidOid4vciRestPublic)
                implementation(projects.libOpenidOid4vciRestImpl)

                // OID4VCI Issuer service logic
                implementation(projects.libOpenidOid4vciIssuerPublic)
                implementation(projects.libOpenidOid4vciIssuerImpl)
                implementation(projects.libOpenidOid4vciCommonPublic)

                // Shared OID4VC types + QR code service
                implementation(projects.libOpenidOid4vcCommonPublic)
                implementation(projects.libOpenidOid4vcCommonImpl)

                // Core
                implementation(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCorePublic)

                // KV storage (for session stores)
                implementation(projects.libDataStoreKvPublic)
                implementation(projects.libDataStoreKvImpl)
                implementation(projects.libDataStoreKvImplMemory)

                // HTTP client
                implementation(projects.libDataLinkHttpClientPublic)

                // DI
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)

                // Serialization & concurrency
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                // Ktor client
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
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

                // Crypto & storage implementations
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libDataLinkHttpClientImpl)

                // CBOR (needed by mdoc format handler DI)
                implementation(projects.libCborImpl)

                // DID manager (for signing key kid resolution via DID providers)
                implementation(projects.libDidManagerPublic)
                implementation(projects.libDidManagerImpl)
                implementation(projects.libDidPersistenceApi)
                implementation(projects.libDidPersistenceMemory)

                // DID resolvers (for proof verification with did:jwk / did:key)
                implementation(projects.libDidResolverImpl)
                implementation(projects.libDidMethodsJwk)
                implementation(projects.libDidMethodsKey)

                // SD-JWT (for JWS verification of proofs)
                implementation(projects.libSdjwtImpl)

                // OAuth2 (for AS bridge)
                implementation(projects.libOauth2CommonPublic)
                implementation(projects.libOauth2ServerAuthorizationPublic)
                implementation(projects.libOauth2ServerAuthorizationImpl)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}
