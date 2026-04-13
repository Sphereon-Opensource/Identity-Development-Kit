plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(libs.plugins.metro)
    id("maven-publish")
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()
    jvm()

    androidLibrary {
        namespace = "com.sphereon.mdoc.transport.ble.impl"
        compileSdk = 35
        minSdk = 27
        withHostTest { }
        withDeviceTest { }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Public API
                api(projects.libMdocTransportBlePublic)
                implementation(projects.libMdocTransportNfc)

                // DI
                implementation(libs.bundles.app.platform.di)

                // Coroutines
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.atomicfu)
                implementation(sphereonlib.co.touchlab.kermit)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCoreApiDefault)
                implementation(libs.amz.metro.impl)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.io.kotest.assertions.core)
                implementation(sphereonlib.io.kotest.framework.engine)
                implementation(sphereonlib.io.kotest.property)

                // BLE test fixtures
                implementation(projects.libDataLinkBleTestFixtures)
                implementation(projects.libDataLinkBleRobots)
            }
        }

        val androidHostTest by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                implementation(libs.amz.metro.impl)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libMdocDatatransferImpl)
                implementation(projects.libMdocCoreImpl)
                implementation(projects.libMdocReader)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libDataLinkBlePublic)
                implementation(projects.libDataLinkBleTestFixtures)
                implementation(projects.libDataLinkBleRobots)
                implementation(sphereonlib.androidx.test.core)
                implementation(sphereonlib.io.mockk.mockk)
                implementation(sphereonlib.app.cash.turbine.turbine)
            }
        }

        val androidDeviceTest by getting {
            dependencies {
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.jdk)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.android)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libMdocDatatransferImpl)
                implementation(projects.libDataLinkBlePublic)
                implementation(libs.bundles.app.platform.di)
                implementation(libs.amz.metro.impl)
                implementation(sphereonlib.androidx.test.rules)
                implementation(kotlin("test"))
                implementation(sphereonlib.androidx.test.ext.junit)
                implementation(sphereonlib.androidx.test.espresso.core)
            }
        }
    }
}

