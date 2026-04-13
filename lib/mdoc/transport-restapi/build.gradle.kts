import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.org.jetbrains.kotlinx.atomicfu)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.npm.publish.org.jetbrains.kotlin.npm.publish.gradle.plugin)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    id("maven-publish")
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}
metro {
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                browser()
                nodejs()
                binaries.library()
            }
        }
    }

    configureIosTargetsIfEnabled()
    configureLinuxTargetIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Transport core abstractions
                api(projects.libMdocCorePublic)

                // HTTP client dependencies
                api(projects.libDataLinkHttpClientPublic)

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
                implementation(projects.libCborImpl)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCoreTest)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.io.kotest.assertions.core)
                implementation(sphereonlib.io.kotest.framework.engine)
                implementation(sphereonlib.io.kotest.property)
                implementation(sphereonlib.io.ktor.client.mock)

                // KMS for proper key generation in tests
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libMdocCoreImpl)
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
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(projects.libCoreApiDefault)
                implementation(sphereonlib.io.mockk.mockk)
            }
        }

        findByName("jsMain")?.dependencies {
            implementation(sphereonlib.io.ktor.client.js)
        }
    }
}
