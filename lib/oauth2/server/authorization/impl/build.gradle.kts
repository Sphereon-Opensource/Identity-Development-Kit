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
    jvm()
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

                // Public API
                api(projects.libOauth2ServerAuthorizationPublic)

                // Core dependencies
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCore)

                api(projects.libDataLinkHttpClientImpl)

                // OAuth2 common models
                api(projects.libOauth2CommonPublic)

                // OAuth2 client implementation for introspection/metadata
                api(projects.libOauth2ClientImpl)

                // Dependency injection
                implementation(libs.bundles.app.platform.di)
                implementation(libs.amz.metro.public)

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
            }
        }
        val jvmTest by getting {
            dependencies {
                // Need default implementations for SessionExecution and other core dependencies
                implementation(projects.libCoreApiDefault)
            }
        }
    }
}

