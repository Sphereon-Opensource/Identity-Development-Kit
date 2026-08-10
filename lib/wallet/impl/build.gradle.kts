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
                api(projects.libWalletWscaPublic)
                // LocalWsca (Wsca policy implementation): impl reaches it via this
                // dependency so its @ContributesBinding merges into the session graph, exactly like the
                // lib-wallet-wscd-software dependency below does for the Wscd/WscdFactory bindings.
                api(projects.libWalletWscaImpl)
                // Software WSCD implementation (SoftwareWscd, SoftwareWscdWalletCredentialBodyProtector,
                // SoftwareWscdBootstrap) lives in its own module; impl reaches it via this dependency.
                api(projects.libWalletWscdSoftware)
                api(projects.libDataStoreBlobPublic)
                api(projects.libDataStoreBlobImpl)
                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                api(projects.libOpenidOid4vciHolderImpl)
                api(projects.libOpenidOid4vpHolderImpl)
                implementation(projects.libMdocCoreImpl)
                // SD-JWT VC verification command (sdjwt.vc.verify): the wallet verifies the
                // issuer signature of an issued SD-JWT VC on receipt before storing it.
                api(projects.libSdjwtPublic)
                // Deliberately NO direct api(projects.libCryptoCoreImpl) edge: no source in this
                // module imports com.sphereon.crypto.core.impl.*
                // directly; lib-crypto-core-impl's non-KMS JOSE/crypto command impls (JwtServiceImpl,
                // JweServiceImpl, DefaultSignatureService, X509VerifyServiceImpl, CryptoServicesImpl)
                // remain compile-visible transitively via libOpenidOid4vciHolderImpl's OWN
                // api(projects.libCryptoCoreImpl) dependency (libOpenidOid4vciHolderImpl is itself an
                // api dependency of this module, so the api chain to lib-crypto-core-impl is
                // unbroken).
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
                // defaultSecureRandom() for the LocalWsca(SoftwareWscd(...), DpopProofAssembly(...))
                // wiring the fake-KMS-backed tests construct directly.
                implementation(projects.libCoreApiDefault)
            }
        }
        val jvmMain by getting {
            dependencies {
                // DefaultRootScopeProvider + DefaultPrincipalMapPropertySource for WalletAppGraph
                api(projects.libCoreApiDefault)
                // Deliberately NO api(projects.libCryptoKmsProviderSoftware) edge: software KMS
                // provider registration lives behind the WSCA/WSCD boundary
                // (SoftwareKmsProviderRegistrar, lib-wallet-wscd-software), which this module already
                // depends on via libWalletWscdSoftware, so no source here needs the KMS provider
                // module directly. libWalletWscdSoftware's OWN dependency on the KMS provider module
                // is `implementation` (provider types must not leak onto consumers' compile
                // classpath), so it does not re-expose it transitively either. If :lib-wallet-impl
                // fails to compile/wire with a "no binding for SoftwareKmsProviderFactory" error,
                // add `implementation(projects.libCryptoKmsProviderSoftware)` here (not `api` -
                // nothing else needs it re-exposed).
                // Event service bindings needed by the App/User/Session graph
                api(projects.libCoreEventsImpl)
                // HTTP client factory + URI command bindings needed by OID4VCI/OID4VP holder impls
                api(projects.libDataLinkHttpClientImpl)
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
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCoreApiDefault)
                // Software KMS provider needed by WalletBootstrap
                implementation(projects.libCryptoKmsProviderSoftware)
                // In-memory backing stores for BlobService (BlobWalletCredentialStore) and KV metadata index
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
