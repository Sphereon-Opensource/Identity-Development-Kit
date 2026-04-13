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
    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }
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
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCore)

                // Universal OID4VP public APIs
                api(projects.libOpenidOid4vpUniversalPublic)

                // OID4VP verifier (reuse existing implementation)
                api(projects.libOpenidOid4vpVerifierPublic)
                api(projects.libOpenidOid4vpVerifierImpl)

                // OID4VP common
                api(projects.libOpenidOid4vpCommonPublic)
                api(projects.libOpenidOid4vpDcql)

                // OID4VC common impl (QR code service)
                api(projects.libOpenidOid4vcCommonImpl)

                // KV storage
                api(projects.libDataStoreKvPublic)
                api(projects.libDataStoreKvImpl)
                api(projects.libDataStoreKvImplMemory)

                // Event system
                api(projects.libCoreEventsPublic)
                api(projects.libCoreEventsImpl)

                // Serialization
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)

                // HTTP client
                api(sphereonlib.io.ktor.client.core)
                api(sphereonlib.io.ktor.client.content.negotiation)
                api(sphereonlib.io.ktor.serialization.kotlinx.json)
                api(projects.libDataLinkHttpClientPublic)

                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCoreTest)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libDidManagerImpl)
                implementation(projects.libDidResolverImpl)
                implementation(projects.libDidMethodsKey)
                implementation(projects.libDidMethodsJwk)
                implementation(projects.libDidPersistenceMemory)
                implementation(projects.libOauth2CommonImpl)
                implementation(projects.libOauth2ClientImpl)
                implementation(projects.libOpenidOid4vpHolderImpl)
                implementation(projects.libSdjwtImpl)
                implementation(sphereonlib.io.ktor.client.mock)
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCoreTest)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libDidManagerImpl)
                implementation(projects.libDidResolverImpl)
                implementation(projects.libDidMethodsKey)
                implementation(projects.libDidMethodsJwk)
                implementation(projects.libDidPersistenceMemory)
                implementation(projects.libOauth2CommonImpl)
                implementation(projects.libOauth2ClientImpl)
                implementation(projects.libOpenidOid4vpHolderImpl)
                implementation(projects.libSdjwtImpl)
                implementation(sphereonlib.io.ktor.client.mock)
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}
