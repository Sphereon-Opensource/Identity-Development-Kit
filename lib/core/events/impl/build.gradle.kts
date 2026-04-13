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
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Events public API
                api(project(":lib-core-events-public"))

                // Core API for SessionContext, Command, scopes
                api(project(":lib-core-api-public"))

                // Kotlin serialization
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)

                // Coroutines for SharedFlow
                api(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                // DateTime
                api(sphereonlib.org.jetbrains.kotlinx.datetime)

                // DI annotations
                api(libs.bundles.app.platform.di)
                api(libs.amz.metro.public)
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

