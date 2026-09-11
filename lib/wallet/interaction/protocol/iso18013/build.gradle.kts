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
    jvm()
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
        val commonMain by getting {
            dependencies {
                api(projects.libWalletInteractionPublic)
                api(projects.libWalletPublic)
                api(projects.libMdocCorePublic)
                api(projects.libMdocDatatransferPublic)
                api(projects.libStatuslistPublic)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libWalletInteractionImpl)
                implementation(projects.libWalletInteractionTestFixtures)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libMdocCoreImpl)
                implementation(projects.libMdocDatatransferImpl)
                implementation(projects.libWalletImpl)
                // Product-boundary mso_mdoc issuance/status verification proof: use the actual
                // OID4VCI format handler and status verifier against the wallet store below.
                implementation(projects.libOpenidOid4vciIssuerImpl)
                // Include the production AS/policy/trust bindings required by the issuer
                // bridge; these are real in-memory/config-backed implementations, not test
                // doubles.
                implementation(projects.libOauth2ServerAuthorizationImpl)
                implementation(projects.libOauth2ServerResourceImpl)
                implementation(projects.libOpenidOid4vciRestImpl)
                implementation(projects.libOpenidOid4vcCommonImpl)
                implementation(projects.libOpenidOid4vpVerifierImpl)
                implementation(projects.libOpenidOid4vpDcqlStoreImpl)
                implementation(projects.libOpenidOid4vpHolderImpl)
                implementation(projects.servicesOid4vciIssuerRest)
                implementation(projects.libTrustCoreImpl)
                implementation(projects.libStatuslistImpl)
                implementation(projects.servicesStatuslistRest)
                implementation(projects.libTrustX509)
                implementation(projects.libDataStoreBlobImplMemory)
                implementation(projects.libDataStoreKvImplMemory)
                implementation(projects.libCoreEventsImpl)
                implementation(sphereonlib.io.ktor.client.mock)
                implementation(projects.libWalletWscdTestFixtures)
                // This source set hosts Iso18013MdocIntegrationTestAppGraph. The wallet
                // graph includes OID4VP holder bindings, whose W3C presentation path
                // requires the Data Integrity command and cryptosuite contributions.
                implementation(projects.libCryptoDataIntegrityProofImpl)
                implementation(projects.libCryptoDataIntegrityProofEddsaJcs2022)
                implementation(projects.libCryptoDataIntegrityProofEddsaRdfc2022)
                implementation(projects.libCryptoDataIntegrityProofEcdsaRdfc2019)
                implementation(project(":lib-openid-oid4vp-verifier-vcdm-impl"))
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(sphereonlib.io.mockk.mockk)
            }
        }
    }
}
