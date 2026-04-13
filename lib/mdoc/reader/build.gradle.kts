import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
    alias(libs.plugins.metro)
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()

    // JVM support for server-side readers (REST API)
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Android support for mobile readers (BLE + NFC + REST API)
    androidLibrary {
        namespace = "com.sphereon.mdoc.reader"
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
    }

    // iOS support for mobile readers (BLE + NFC + REST API)
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()


    sourceSets {
        val commonMain by getting {
            dependencies {
                // Core APIs
                api(projects.libCoreApiPublic)
                api(projects.libCoreApiDefault)
                api(projects.libCoreCompat)

                // CBOR and crypto
                api(projects.libCborPublic)
                api(projects.libCryptoCorePublic)

                // mDoc core and transport
                api(projects.libMdocCorePublic)
//                api(projects.libMdocTransportCore)
                // REST API transport: moved to JVM-only (doesn't support iOS native)

                // TODO: Remove this dependency once engagement classes are extracted to shared module
                // Currently needed for EngagementInstance, MdocEngagementFactory, etc.
                // NOTE: This means reader currently only works on Android/iOS, not JVM
                api(projects.libMdocDatatransferPublic)

                // DI
                implementation(libs.bundles.app.platform.di)

                // Coroutines and serialization
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.cbor)

                // Crypto provider
                implementation(sphereonlib.dev.whyoleg.cryptography.core)

                // Logging
                implementation(sphereonlib.co.touchlab.kermit)
            }
        }

        val jvmMain by getting {
            dependencies {
                // JVM: REST API transport (JVM/JS only, not iOS)
                implementation(projects.libMdocTransportRestapi)
            }
        }

        val androidMain by getting {
            dependencies {
                // Android: Add BLE and NFC transports
                implementation(projects.libMdocTransportBlePublic)
                implementation(projects.libMdocTransportNfc)
                implementation(sphereonlib.androidx.core.ktx)
            }
        }

        val iosMain by getting {
            dependencies {
                // iOS: Add BLE and NFC transports
                implementation(projects.libMdocTransportBlePublic)
                implementation(projects.libMdocTransportNfc)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCoreImpl)
                implementation(libs.amz.metro.impl)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.io.kotest.assertions.core)
                implementation(sphereonlib.io.kotest.framework.engine)
                implementation(sphereonlib.io.kotest.property)
                implementation(projects.libCryptoKmsProviderSoftware)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.jdk)
                implementation(projects.libCryptoKmsProviderSoftware)
            }
        }

        val androidHostTest by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.jdk)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(libs.amz.metro.impl)
                implementation(projects.libCoreApiDefault)
                implementation(sphereonlib.io.mockk.mockk)
                implementation(sphereonlib.app.cash.turbine.turbine)
            }
        }
    }
}

