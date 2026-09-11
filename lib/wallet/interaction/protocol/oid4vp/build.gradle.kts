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
                api(projects.libWalletWscaPublic)
                api(projects.libCryptoDataIntegrityProofPublic)
                // Production Data Integrity holder signing uses the concrete cryptosuites and
                // JsonLdProcessor (see Oid4vpDataIntegrityCryptosuiteSigner).
                implementation(projects.libCryptoDataIntegrityProofEddsaJcs2022)
                implementation(projects.libCryptoDataIntegrityProofEddsaRdfc2022)
                implementation(projects.libCryptoDataIntegrityProofEcdsaRdfc2019)
                api(projects.libJsonldPublic)
                implementation(projects.libJsonldProcessor)
                api(projects.libOpenidOid4vpHolderPublic)
                implementation(projects.libOpenidOid4vpHolderImpl)
                api(projects.libOpenidOid4vpCommonPublic)
                api(projects.libOpenidOid4vpDcql)
                // SD-JWT codec (parse/serialize) reused by SecureComponentOid4vpSdJwtHolderBindingProvider
                // to pre-sign the RFC 9901 Key Binding JWT through Wsca instead of the generic
                // holder's KMS-managed-identifier path.
                api(projects.libSdjwtPublic)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCryptoDataIntegrityProofImpl)
                implementation(projects.libCryptoDataIntegrityProofEddsaJcs2022)
                implementation(projects.libCryptoDataIntegrityProofEddsaRdfc2022)
                implementation(projects.libCryptoDataIntegrityProofEcdsaRdfc2019)
                implementation(projects.libJsonldLoader)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}
