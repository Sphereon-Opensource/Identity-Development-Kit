import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    id("maven-publish")
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

    androidLibrary {
        namespace = "com.sphereon.mdoc.datatransfer.impl"
        compileSdk = 35
        minSdk = 27
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        packaging {
            resources {
                excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            }
        }
        withHostTest { }
        withDeviceTest { }
    }

    configureIosTargetsIfEnabled()
    configureLinuxTargetIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Public API
                api(projects.libMdocDatatransferPublic)

                // DI dependencies
                implementation(libs.bundles.app.platform.di)
//                api(projects.libCoreApiDefault)
//                api(projects.libCryptoCoreImpl)
//                api(projects.libMdocCoreImpl)
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCorePublic)
                api(projects.libMdocCorePublic)
                implementation(projects.libMdocCoreImpl)

                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.cbor)
                implementation(sphereonlib.dev.whyoleg.cryptography.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.atomicfu)
                implementation(sphereonlib.co.touchlab.kermit)
            }
        }

        val jvmMain by getting {
            dependencies {
            }
        }

        // Android/iOS have BLE/NFC platform-specific implementations
        findByName("androidMain")?.dependencies {
            // NFC/BLE dependencies now in commonMain
            implementation(sphereonlib.androidx.core.ktx)
        }

        // iosMain: no platform-specific config needed (created by hierarchy template when iOS targets enabled)

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCborImpl)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCryptoCoreImpl)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.io.kotest.assertions.core)
                implementation(sphereonlib.io.kotest.framework.engine)
                implementation(sphereonlib.io.kotest.property)
                implementation(projects.libCryptoKmsProviderSoftware)
            }
        }

        val androidHostTest by getting {
            dependencies {
                implementation(projects.libMdocCoreImpl)
                implementation(projects.libMdocReader)
                implementation(projects.libCryptoCoreImpl)
            }
        }

        androidHostTest.dependencies {
            implementation(libs.bundles.app.platform.di)
            implementation(sphereonlib.dev.whyoleg.cryptography.provider.jdk)
            implementation(projects.libCryptoCoreImpl)
            implementation(projects.libCryptoKmsProviderSoftware)
            implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            implementation(projects.libCoreApiDefault)
            implementation(projects.libMdocCoreImpl)
            implementation(sphereonlib.io.mockk.mockk)
            implementation(sphereonlib.app.cash.turbine.turbine)
        }

        val androidDeviceTest by getting {
            dependencies {
                implementation(sphereonlib.io.github.g0dkar.qrcode.kotlin)
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.jdk)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.android)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoCorePublic)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libMdocCoreImpl)
                implementation(projects.libMdocReader)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(sphereonlib.io.mockk.mockk)
                implementation(sphereonlib.app.cash.turbine.turbine)
                implementation(sphereonlib.androidx.test.rules)
                implementation(kotlin("test"))
                implementation(sphereonlib.androidx.test.ext.junit)
                implementation(sphereonlib.androidx.test.espresso.core)
            }
        }
    }
}
