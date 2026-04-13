import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.npm.publish.org.jetbrains.kotlin.npm.publish.gradle.plugin)
    alias(sphereonplug.plugins.org.jetbrains.kotlinx.atomicfu)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
    alias(libs.plugins.metro)
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    js {
        browser()
        nodejs()
        binaries.library()
    }

    iosArm64()
    iosX64()
    iosSimulatorArm64()
    linuxX64()


    sourceSets {
        val commonMain by getting {
            dependencies {
                // Transport core abstractions
                api(projects.libMdocCorePublic)

                // HTTP client dependencies
                api(projects.libDataLinkHttpClientPublic)
                api(projects.libOpenidOid4vpHolderPublic)

                // DI
                implementation(libs.bundles.app.platform.di)

                // Coroutines
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.co.touchlab.kermit)

                // Ktor HTTP client
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCoreTest)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(libs.amz.metro.impl)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.io.kotest.assertions.core)
                implementation(sphereonlib.io.kotest.framework.engine)
                implementation(sphereonlib.io.kotest.property)
                implementation(sphereonlib.io.ktor.client.mock)

                // KMS for proper key generation in tests
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(sphereonlib.io.ktor.client.cio.jvm)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                implementation(libs.amz.metro.impl)
                implementation(projects.libCoreApiDefault)
                implementation(sphereonlib.io.mockk.mockk)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(sphereonlib.io.ktor.client.js)
            }
        }

    }
}

