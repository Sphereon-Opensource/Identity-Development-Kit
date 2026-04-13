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
        findByName("jsTest")?.dependencies {
            implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core.js)
        }
        val commonMain by getting {
            dependencies {
                implementation(sphereonlib.co.touchlab.skie.configuration.annotations)
                api(projects.libOpenidOid4vciIssuerPublic)
                api(projects.libOpenidOid4vciCommonImpl)
                api(projects.libCoreEventsPublic)

                // SD-JWT issuance
                api(projects.libSdjwtPublic)
                api(projects.libSdjwtImpl)

                // Crypto (JWT/JWE, managed identifiers)
                api(projects.libCryptoCoreImpl)

                // DID provider registry (for signing key mode DID resolution)
                api(projects.libDidManagerPublic)

                // mDoc (ISO 18013-5 mobile documents)
                api(projects.libMdocCorePublic)
                api(projects.libMdocCoreImpl)

                // KV store
                api(projects.libDataStoreKvImpl)
                api(projects.libDataStoreKvImplMemory)

                // Credential design (for design-backed config provider)
                api(projects.libDataStoreCredentialDesignPublic)
                implementation(projects.libDataStoreCredentialDesignImpl)

                // OAuth2 AS (for SphereonAsBridge)
                api(projects.libOauth2ServerAuthorizationPublic)

                // Konform for validation
                api(sphereonlib.io.konform.konform)

                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCryptoCorePublic)
                implementation(projects.libCryptoCoreImpl)
            }
        }
    }
}
