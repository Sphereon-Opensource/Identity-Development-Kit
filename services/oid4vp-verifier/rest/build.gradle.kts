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
                // OID4VP Verifier + Universal service logic
                implementation(projects.libOpenidOid4vpVerifierPublic)
                implementation(projects.libOpenidOid4vpVerifierImpl)
                implementation(projects.libOpenidOid4vpUniversalPublic)
                implementation(projects.libOpenidOid4vpUniversalImpl)
                implementation(projects.libOpenidOid4vpCommonPublic)
                implementation(projects.libOpenidOid4vpDcql)

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

                // Events
                implementation(projects.libCoreEventsImpl)
            }
        }
    }
}
