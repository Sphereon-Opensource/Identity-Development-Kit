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
            testTask { useMocha { timeout = "60000" } }
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
                api(projects.libOauth2CommonPublic)

                // Core dependencies
                implementation(projects.libCoreApiPublic)
                implementation(projects.libCryptoCoreImpl)

                // Dependency injection
                implementation(libs.bundles.app.platform.di)
                implementation(libs.amz.metro.public)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCoreApiDefault)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCoreApiDefault)
            }
        }
    }
}

