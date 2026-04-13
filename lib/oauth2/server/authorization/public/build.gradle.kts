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

                // Core IDK dependencies
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCore)

                // OAuth2 common models (shared between client, AS, RS)
                api(projects.libOauth2CommonPublic)
                // OAuth2 client models (for request/response types)
                api(projects.libOauth2ClientPublic)

                // Dependency injection
                api(libs.bundles.app.platform.di)
                api(libs.amz.metro.public)

                // Konform for validation
                api(sphereonlib.io.konform.konform)

                // Ktor for HTTP (if needed for PAR/introspection)
                api(sphereonlib.io.ktor.client.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}

