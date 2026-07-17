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
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCorePublic)
                // api, and deliberately so: this WSCD module is the ONLY sanctioned owner of the
                // software KMS provider dependency (verifyWalletKmsDependencyRule forbids it
                // everywhere else in the wallet trees), and the provider module carries Metro
                // AppScope contributions (SoftwareKmsProviderFactory into Set<KmsProviderFactory>,
                // SerializerRegistration) that must be compile-visible where the final app graphs
                // are compiled (WalletProductAppGraph, WalletRunnerAppGraph). Riding the api chain
                // of THIS module is how those graphs get that closure without declaring a KMS
                // dependency themselves. Misuse is still blocked one level down:
                // verifyWalletKmsBoundary fails any raw com.sphereon.crypto.core.kms /
                // com.sphereon.crypto.kms.provider IMPORT outside lib/wallet/wscd, so the widened
                // compile-time visibility cannot become code coupling.
                api(projects.libCryptoKmsProviderSoftware)
                api(projects.libWalletUnitPublic)
                api(projects.libWalletWscdPublic)
                // WalletCredentialBodyProtector contract (implemented by the software WSCD
                // body protector) lives in lib-wallet-public's credential package until the
                // store contracts re-home (P2+); no cycle: public never depends on this module.
                api(projects.libWalletPublic)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
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
                // SoftwareWscdFactoryTest/SoftwareWscdTest are real-crypto (software-KMS backed): they
                // build a full session graph via createWalletAppGraph to get at a wired KeyManagerService.
                // com.sphereon.wallet.impl.di.createWalletAppGraph: the test's real-KMS graph builder
                // lives in lib-wallet-impl. This is a test-only, JVM-only dependency back onto
                // lib-wallet-impl; it is not a build cycle because it uses a different configuration
                // (jvmTestImplementation of this module) than the one lib-wallet-impl depends on (this
                // module's jvm main/api). It transitively supplies the DID/sdjwt/mdoc/memory-store/event
                // impls createWalletAppGraph's Metro graph needs at runtime, since lib-wallet-impl exposes
                // those as `api` in its own jvmMain.
                implementation(projects.libWalletWscdTestFixtures)
            }
        }
    }
}
