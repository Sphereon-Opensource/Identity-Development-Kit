import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    id("maven-publish")
}
metro {
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
    androidLibrary {
        namespace = "com.sphereon.data.link.nfc"
        compileSdk = 35
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        packaging {
            resources {
                excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            }
        }
        withDeviceTest { }
    }

    /*js {
        browser()
        nodejs()
    }*/

    configureIosTargetsIfEnabled()

    // Configure iOS framework for Xcode integration

    /*listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "NfcMdocEngagement"
            isStatic = true

            // Add compiler options for better Objective-C interop
            freeCompilerArgs += listOf(
                "-Xobjc-generics"
            )

            // Export dependencies to make them available in Swift
            export(projects.libDataLinkBlePublic)
            export(projects.libDataLinkNfcPublic)
            export(projects.libMdocCore)
        }
    }*/

    sourceSets {
        val commonMain by getting {
            dependencies {
//                implementation(projects.libCoreApiPublic)
//                implementation(projects.libCryptoCore)
//                implementation(projects.libCoreApiDefault)

                implementation(libs.bundles.app.platform.di)
//                api(libs.bundles.app.platform.di)
                api(projects.libDataLinkBlePublic)
                api(projects.libDataLinkNfcPublic)
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.client.logging)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
//                api(projects.libMdocCore)
            }
        }
       /* val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(sphereonlib.io.ktor.server.test.host)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }*/
        val jvmMain by getting {
            dependencies {
                api(sphereonlib.io.ktor.client.cio.jvm)
                api(sphereonlib.io.ktor.client.okhttp.jvm)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCryptoCore)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libCborPublic)
                implementation(sphereonlib.io.ktor.server.core)
                implementation(sphereonlib.io.ktor.server.jetty)
                implementation(sphereonlib.io.ktor.client.mock)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
                implementation(sphereonlib.io.ktor.client.logging)
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.org.bouncycastle.bcprov.jdk18on)
                implementation(sphereonlib.org.bouncycastle.bcpkix.jdk18on)
            }
        }

        findByName("androidMain")?.dependencies {
            implementation(sphereonlib.androidx.core.ktx)
            api(projects.libCryptoKmsProviderSoftware)
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(sphereonlib.io.github.g0dkar.qrcode.kotlin)

                implementation(sphereonlib.dev.whyoleg.cryptography.provider.jdk)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.android)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCoreApiDefault)

                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.androidx.test.rules)
                implementation(kotlin("test"))
                implementation(sphereonlib.androidx.test.ext.junit)
                implementation(sphereonlib.androidx.test.espresso.core)
            }
        }

        /*val jsMain by getting {
            dependencies {
                implementation(sphereonlib.io.ktor.client.cio.js)
            }
        }

        val jsTest by getting {
            dependencies {
            }
        }*/
    }
}
