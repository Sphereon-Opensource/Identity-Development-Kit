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
                // The Tier 1 <-> Tier 2 holder wiring seam (spec section 3.7/1): the ONE place that
                // contributes the real OID4VCI/OID4VP protocol adapters into the SessionScope
                // Set<WalletInteractionProtocolAdapter> multibinding, so both wallet-app-impl's own
                // product graph and wallet-runner's graph resolve REAL flow execution instead of each
                // declaring a competing, duplicate wiring (Metro rejects two @Multibinds declarations
                // for the same key on one graph).
                api(projects.libWalletInteractionPublic)
                api(projects.libWalletWscaPublic)
                implementation(projects.libWalletInteractionImpl)
                implementation(projects.libDataStoreKvImpl)
                implementation(projects.libWalletInteractionProtocolOid4vci)
                implementation(projects.libWalletInteractionProtocolOid4vp)
                implementation(projects.libWalletPublic)
                implementation(projects.libOpenidOid4vciHolderPublic)
                implementation(projects.libOpenidOid4vpHolderPublic)
                implementation(projects.libOpenidOid4vpCommonPublic)
                implementation(projects.libSdjwtPublic)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}
