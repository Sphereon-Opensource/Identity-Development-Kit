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
    kotlin.applyDefaultHierarchyTemplate()
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
        browser()
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
        val commonMain by getting {
            dependencies {
                // Public API
                api(projects.libDataLinkHttpClientPublic)

                // DI dependencies
                api(libs.bundles.app.platform.di)
                api(libs.amz.metro.public)
                implementation(libs.amz.metro.impl)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.io.ktor.server.test.host)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCoreTest)
                implementation(projects.libCoreApiDefault)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(sphereonlib.io.ktor.client.cio.jvm)
                api(sphereonlib.io.ktor.client.okhttp.jvm)
                api(projects.libCryptoCorePublic)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCryptoCore)
                implementation(projects.libCoreTest)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libCborPublic)
                implementation(sphereonlib.io.ktor.server.core)
                implementation(sphereonlib.io.ktor.server.jetty)
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.client.mock)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
                implementation(sphereonlib.io.ktor.client.logging)
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(libs.bcprov.jdk18on)
                implementation(libs.bcpkix.jdk18on)
            }
        }
        val jsMain by getting {
            dependencies {
                api(sphereonlib.io.ktor.client.js)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                api(sphereonlib.io.ktor.client.cio.wasm.js)
            }
        }
        val appleMain by getting {
            dependencies {
                api(sphereonlib.io.ktor.client.darwin)
                api(projects.libCryptoCorePublic)
            }
        }
    }
}

