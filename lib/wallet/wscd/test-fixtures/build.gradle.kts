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
                api(projects.libWalletWscaPublic)
                api(projects.libWalletWscdPublic)
                api(projects.libCryptoKeyPersistenceApi)
                // Sanctioned raw-KMS wiring for tests (TestWscdSupport): this
                // module lives under lib/wallet/wscd/, so the boundary guards exempt it. Not
                // re-exported (implementation): consumers get a KeyManagerService handle back from
                // TestWscdSupport, never a provider type directly.
                implementation(projects.libCryptoKmsProviderSoftware)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                // WalletAppGraph + WalletBootstrap (dev/test composition roots) live here per the
                // repo rule that @DependencyGraph belongs only in final apps and test support, never
                // in a library's jvmMain. The graph composes lib-wallet-impl's bindings and needs the
                // software KMS provider factory binding on its compile classpath.
                api(projects.libWalletImpl)
                api(projects.libCryptoKmsProviderSoftware)
                // WalletAppGraph is a final composition root. OID4VP holder support now
                // creates W3C Data Integrity presentations through AddProofServiceCommand,
                // so the command implementation and supported cryptosuite contributions
                // must be visible to Metro when this graph is generated.
                implementation(projects.libCryptoDataIntegrityProofImpl)
                implementation(projects.libCryptoDataIntegrityProofEddsaJcs2022)
                implementation(projects.libCryptoDataIntegrityProofEddsaRdfc2022)
                implementation(projects.libCryptoDataIntegrityProofEcdsaRdfc2019)
                // The fixture graph uses a real durable SQLite DID repository. Production memory
                // factories were deliberately removed from the graph.
                api(projects.libDidPersistenceSqlite)
                // Include the persistent managed-key selector so the graph's KeyManagerService
                // delegates registered-key authority lookups to the shared in-memory fixture store.
                implementation(projects.libCryptoKeyPersistenceImpl)
            }
        }
    }
}
