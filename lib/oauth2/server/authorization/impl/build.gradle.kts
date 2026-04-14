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
    applyDefaultHierarchyTemplate()
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

                // Public API
                api(projects.libOauth2ServerAuthorizationPublic)

                // OID4VP verifier service (optional at runtime — AS works without it)
                api(projects.libOpenidOid4vpVerifierPublic)

                // OID4VCI issuer public (for CredentialIssuancePolicyResolver — optional at runtime)
                api(projects.libOpenidOid4vciIssuerPublic)

                // Core dependencies
                api(projects.libCoreApiPublic)
                api(projects.libCoreEventsPublic)
                api(projects.libCryptoCore)

                api(projects.libDataLinkHttpClientImpl)

                // OAuth2 common models
                api(projects.libOauth2CommonPublic)

                // OAuth2 client implementation for introspection/metadata
                api(projects.libOauth2ClientImpl)

                // Dependency injection
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)

                // KV store (IAE session storage)
                api(projects.libDataStoreKvPublic)
                api(projects.libDataStoreKvImpl)
                api(projects.libDataStoreKvImplMemory)

                // Ktor for HTTP operations
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                // OAuth2 client implementation for introspection/metadata

                implementation(projects.libOauth2ClientImpl)
                implementation(projects.libCoreTest)
                implementation(projects.libCryptoKmsProviderSoftware)
                // Need default implementations for SessionExecution and other core dependencies
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCoreEventsImpl)
            }
        }
    }
}
