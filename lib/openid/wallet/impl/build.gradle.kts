import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}
metro {
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libWalletPublic)
                api(projects.libDataStoreBlobPublic)
                api(projects.libDataStoreBlobImpl)
                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                api(projects.libOpenidOid4vciHolderImpl)
                api(projects.libOpenidOid4vpHolderImpl)
                // SD-JWT VC verification command (sdjwt.vc.verify): the wallet verifies the
                // issuer signature of an issued SD-JWT VC on receipt before storing it.
                api(projects.libSdjwtPublic)
                api(projects.libCryptoCoreImpl)
                api(projects.libOauth2ClientImpl)
                api(projects.libDataLinkHttpClientPublic)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libDataStoreBlobImplMemory)
                implementation(projects.libDataStoreKvImplMemory)
                implementation(projects.libCoreEventsImpl)
            }
        }
        val jvmMain by getting {
            dependencies {
                // DefaultRootScopeProvider + DefaultPrincipalMapPropertySource for WalletAppGraph
                api(projects.libCoreApiDefault)
                // Software KMS provider: WalletBootstrap registers it so createHolderKey() works
                api(projects.libCryptoKmsProviderSoftware)
                // Event service bindings needed by the App/User/Session graph
                api(projects.libCoreEventsImpl)
                // HTTP client factory + URI command bindings needed by OID4VCI/OID4VP holder impls
                api(projects.libDataLinkHttpClientImpl)
                // In-memory KV store for blob metadata index (BlobWalletDocumentStore)
                api(projects.libDataStoreKvImplMemory)
                // In-memory blob backing store (BlobWalletDocumentStore)
                api(projects.libDataStoreBlobImplMemory)
                // OAuth2 common impl (CreateJarmResponseCommandImpl, VerifyJarmResponseCommandImpl):
                // pulled in transitively at runtime via libOauth2ClientImpl but NOT on compile
                // classpath; Metro @MergeComponent scanning misses it without an explicit dep.
                api(projects.libOauth2CommonImpl)
                // SD-JWT format handler needed by OID4VC holder impl
                api(projects.libSdjwtImpl)
                // CBOR / mDoc format handlers needed by OID4VP holder impl
                api(projects.libCborImpl)
                api(projects.libMdocCoreImpl)
                // DID manager + resolver + method impls needed by holder credential proofs
                api(projects.libDidManagerImpl)
                api(projects.libDidResolverImpl)
                api(projects.libDidMethodsJwk)
                api(projects.libDidPersistenceMemory)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCoreApiDefault)
                // Software KMS provider needed by WalletBootstrap
                implementation(projects.libCryptoKmsProviderSoftware)
                // In-memory backing stores for BlobService (BlobWalletDocumentStore) and KV metadata index
                implementation(projects.libDataStoreBlobImplMemory)
                implementation(projects.libDataStoreKvImplMemory)
                // Event hub used by DefaultBlobService
                implementation(projects.libCoreEventsImpl)
                // DID method impls pulled in by OID4VCI/OID4VP holder graphs
                implementation(projects.libDidMethodsJwk)
                implementation(projects.libDidPersistenceMemory)
                // HTTP client impl needed by holder metadata resolution
                implementation(projects.libDataLinkHttpClientImpl)
                // SD-JWT format handler
                implementation(projects.libSdjwtImpl)
                // CBOR/mDoc format handlers
                implementation(projects.libCborImpl)
                implementation(projects.libMdocCoreImpl)
                // DID manager impl
                implementation(projects.libDidManagerImpl)
                // DID resolver impl
                implementation(projects.libDidResolverImpl)
            }
        }
    }
}
