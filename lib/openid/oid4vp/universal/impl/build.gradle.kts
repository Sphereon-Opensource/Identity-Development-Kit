import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.metro)
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }
    js {
        compilerOptions {
            moduleKind = JsModuleKind.MODULE_ES
            target = "es2015"
            freeCompilerArgs.add("-Xes-long-as-bigint")
        }
        nodejs {
            useEsModules()
            binaries.executable()
            generateTypeScriptDefinitions()
        }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        val jsTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core.js)
            }
        }
        val commonMain by getting {
            dependencies {
                implementation(libs.skie.configuration.annotations)
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
                api(libs.amz.metro.public)
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
                implementation(projects.libOauth2CommonImpl)
                implementation(projects.libOauth2ClientImpl)
                implementation(projects.libOpenidOid4vpHolderImpl)
                implementation(projects.libSdjwtImpl)
                implementation(sphereonlib.io.ktor.client.mock)
            }
        }
        val jvmMain by getting {
            dependencies {
                // QR code generation (JVM only)
                implementation(sphereonlib.io.github.g0dkar.qrcode.kotlin)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCoreTest)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
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

