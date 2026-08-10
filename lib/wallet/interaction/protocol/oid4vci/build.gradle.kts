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
                api(projects.libOpenidOid4vciHolderPublic)
                api(projects.libOpenidOid4vciCommonPublic)
                implementation(projects.libOauth2CommonPublic)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                // SD-JWT VC verification command (sdjwt.vc.verify): the receiver verifies the
                // issuer signature of an issued SD-JWT VC on receipt before storing it.
                api(projects.libSdjwtPublic)
                // mDoc CBOR codecs (IssuerSignedCborCodec / MobileSecurityObjectCborCodec): the
                // receiver derives the actual mdoc doctype from the issued payload.
                implementation(projects.libMdocCoreImpl)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libWalletImpl)
                implementation(projects.libDataStoreBlobImplMemory)
                implementation(projects.libDataStoreKvImplMemory)
                implementation(projects.libCoreEventsImpl)
            }
        }
    }
}
