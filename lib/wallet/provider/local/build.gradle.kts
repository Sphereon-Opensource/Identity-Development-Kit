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
                api(projects.libWalletProviderPublic)
                implementation(projects.libWalletPartyPublic)
                // Wsca (the ONLY signing surface this module is allowed to touch - Global
                // Constraints, KMS boundary) + Wscd (WscdProfile, for the honest signer-profile
                // mapping) + WalletUnitPublic (WalletAttestedKeyRef, SecureComponentUsage, TS03
                // claim/encoder models). All three are already transitive via
                // libWalletProviderPublic/libWalletWscaPublic; listed explicitly for clarity,
                // mirroring lib-wallet-wsca-impl's own build file.
                api(projects.libWalletWscaPublic)
                api(projects.libWalletWscdPublic)
                api(projects.libWalletUnitPublic)
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
                // Hermetic real-crypto test graph, mirroring lib-wallet-wsca-impl's own jvmTest
                // (LocalWscaTest): this module's production code never depends on KMS providers or
                // lib-wallet-wsca-impl/lib-wallet-impl; these are test-only, JVM-only dependencies
                // (guard exemption is test-only - verifyWalletKmsBoundary excludes **/src/*Test/**).
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libOauth2CommonPublic)
                implementation(projects.libWalletWscaImpl)
                implementation(projects.libWalletWscdTestFixtures)
            }
        }
    }
}
