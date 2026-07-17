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
                // Wsca (this module's contract) + Wscd (the ONLY custody access LocalWsca is
                // allowed). Deliberately NO lib-crypto-kms-* / lib-crypto-core-impl
                // dependency anywhere in this file - verifyWalletKmsDependencyRule enforces that for any
                // wallet module outside lib-wallet-wscd-*.
                api(projects.libWalletWscaPublic)
                api(projects.libWalletWscdPublic)
                api(projects.libWalletUnitPublic)
                // DPoP proof assembly seam (com.sphereon.oauth2.common.command.DpopProofAssembly): the
                // same header/payload/signing-input builder CreateDpopProofCommandImpl uses, so this
                // module never re-implements DPoP JWT assembly. Note: lib-oauth2-client-public is
                // deliberately NOT a dependency here - LocalWsca imports nothing from it, only from
                // oauth2-common-public (where CreateDpopProofCommand/DpopJwtHeader/DpopProofAssembly
                // actually live).
                api(projects.libOauth2CommonPublic)
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCorePublic)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jvmTest by getting {
            dependencies {
                // defaultSecureRandom() for the test's DpopProofAssembly wiring.
                implementation(projects.libCoreApiDefault)
                // Software KMS provider: test-only wiring so LocalWscaTest can build a real,
                // software-KMS-backed SoftwareWscd session, mirroring the pattern in
                // lib-wallet-wscd-software's own jvmTest. This module's production (commonMain) code
                // never depends on KMS providers; guard exemption is test-only (verifyWalletKmsBoundary
                // excludes **/src/*Test/**, verifyWalletKmsDependencyRule exempts test configurations).
                implementation(projects.libCryptoKmsProviderSoftware)
                // com.sphereon.wallet.impl.di.createWalletAppGraph: the test's real-KMS graph builder
                // lives in lib-wallet-impl. Test-only, JVM-only dependency back onto lib-wallet-impl; not
                // a build cycle because it uses a different configuration (jvmTestImplementation of this
                // module) than the one lib-wallet-impl depends on (this module's jvm main/api).
                implementation(projects.libWalletWscdTestFixtures)
            }
        }
    }
}
