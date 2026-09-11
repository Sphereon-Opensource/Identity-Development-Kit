import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

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
    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                compilerOptions {
                    moduleKind = JsModuleKind.MODULE_ES
                    target = "es2015"
                }
                browser { testTask { enabled = false } }
                nodejs { testTask { useMocha { timeout = "60000" } } }
                binaries.library()
                generateTypeScriptDefinitions()
            }
        }
    }
    configureWasmJsTargetIfEnabled {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    configureIosTargetsIfEnabled()
    configureLinuxTargetIfEnabled()

    sourceSets {
        findByName("jsTest")?.dependencies {
            implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core.js)
        }
        val commonMain by getting {
            dependencies {
                implementation(sphereonlib.co.touchlab.skie.configuration.annotations)
                api(projects.libCoreApiPublic)
                api(projects.libCoreEventsPublic)
                api(projects.libCryptoCore)
                api(projects.libCryptoCorePublic)

                // VCDM Data Integrity facade and identifier-neutral
                // verification-method resolution used by the common-platform
                // verification adapter. Concrete identifier resolvers are
                // supplied by the deployment graph.
                api(projects.libCryptoDataIntegrityProofPublic)

                // DID manager (for did:jwk kid resolution in JAR signing)
                api(projects.libDidManagerPublic)
                api(projects.libDidCorePublic)

                // OAuth2 common and client (for JAR support)
                api(projects.libOauth2CommonPublic)
                api(projects.libOauth2ClientPublic)

                // OID4VP public APIs
                api(projects.libOpenidOid4vpVerifierPublic)
                api(projects.libOpenidOid4vpCommonPublic)
                api(projects.libOpenidOid4vpDcql)
                api(projects.libOpenidOid4vpDcqlStorePublic)

                // KV storage (for KV-backed OID4VP stores)
                api(projects.libDataStoreKvPublic)
                api(projects.libDataStoreKvImpl)
                api(projects.libDataStoreKvImplMemory)

                // SD-JWT verification
                api(projects.libSdjwtPublic)

                // Credential status verification SPI + evaluation (impls supplied by the deployment)
                implementation(projects.libStatuslistPublic)

                // JSON-LD context + schema validation for VCDM 2.0
                // (jwt_vc_json-ld) presentations. Mirrors the wiring on the
                // issuer side in lib-openid-oid4vci-issuer-impl.
                api(projects.libJsonldPublic)
                implementation(projects.libJsonldLoader)

                // mDoc verification (Fix V-P0-11): MdocValidations + DeviceAuthValidation +
                // SessionTranscriptCborCodec + DeviceResponseCborCodec for OID4VP iso_mdl path.
                api(projects.libMdocCorePublic)

                // mDoc IACA trust anchors flow through the shared X.509 trust loader
                // (`trust.anchors.x509.*` config keys). Same loader X509TrustValidationService uses.
                implementation(projects.libTrustX509)

                // Serialization
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)

                // Validation (konform)
                api(sphereonlib.io.konform.konform)

                // HTTP client for metadata resolution
                api(sphereonlib.io.ktor.client.core)
                api(sphereonlib.io.ktor.client.content.negotiation)
                api(sphereonlib.io.ktor.serialization.kotlinx.json)
                // HttpClientFactory abstraction (used for callbacks)
                api(projects.libDataLinkHttpClientPublic)

                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCoreTest)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCoreEventsImpl)
                // The verifier implementation consumes the VCDM port only. Concrete VCDM and
                // Data Integrity implementations belong to the final test/app graph.
                implementation(project(":lib-openid-oid4vp-verifier-vcdm-impl"))
                implementation(projects.libCryptoDataIntegrityProofImpl)
                implementation(projects.libCryptoDataIntegrityProofEddsaJcs2022)
                implementation(projects.libCryptoDataIntegrityProofEddsaRdfc2022)
                implementation(projects.libCryptoDataIntegrityProofEcdsaRdfc2019)
                // KvDcqlQueryConfigurationStore binding for the verifier test app graph
                implementation(projects.libOpenidOid4vpDcqlStoreImpl)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libDidManagerImpl)
                implementation(projects.libDidResolverImpl)
                implementation(projects.libDidMethodsKey)
                implementation(projects.libDidMethodsJwk)
                implementation(projects.libDidPersistenceMemory)
                implementation(projects.libOauth2CommonImpl)
                implementation(projects.libOauth2ClientImpl)
                implementation(projects.libOpenidOid4vpHolderImpl)
                implementation(projects.libSdjwtImpl)
                // mdoc impl bindings (MdocValidations, DeviceAuthValidation, *CborCodec impls)
                // are required for the test app graph to satisfy VerifyHolderBindingCommandImpl's
                // mdoc dependencies. Pulls in the CBOR + COSE impls transitively.
                implementation(projects.libMdocCoreImpl)
                implementation(projects.libCborImpl)
                // DefaultTrustConfigProvider binding for X509TrustAnchorLoaderImpl.
                implementation(projects.libTrustCoreImpl)
                implementation(sphereonlib.io.ktor.client.mock)
            }
        }
        val jvmTest by getting {
            // Keep the canonical W3C vectors in oid4vc/common/public; expose that one
            // resource tree to this production-verifier test source set without duplicating
            // fixtures into the verifier module.
            resources.srcDir(rootProject.file("lib/openid/oid4vc/common/public/src/jvmTest/resources"))
            dependencies {
                implementation(projects.libCoreTest)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCoreEventsImpl)
                implementation(project(":lib-openid-oid4vp-verifier-vcdm-impl"))
                implementation(projects.libCryptoDataIntegrityProofImpl)
                implementation(projects.libCryptoDataIntegrityProofEddsaJcs2022)
                implementation(projects.libCryptoDataIntegrityProofEddsaRdfc2022)
                implementation(projects.libCryptoDataIntegrityProofEcdsaRdfc2019)
                implementation(projects.libOpenidOid4vpDcqlStoreImpl)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libDidManagerImpl)
                implementation(projects.libDidResolverImpl)
                implementation(projects.libDidMethodsKey)
                implementation(projects.libDidMethodsJwk)
                implementation(projects.libDidPersistenceMemory)
                implementation(projects.libOauth2CommonImpl)
                implementation(projects.libOauth2ClientImpl)
                implementation(projects.libOpenidOid4vpHolderImpl)
                implementation(projects.libSdjwtImpl)
                implementation(projects.libMdocCoreImpl)
                implementation(projects.libCborImpl)
                implementation(projects.libTrustCoreImpl)
                implementation(sphereonlib.io.ktor.client.mock)
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}
