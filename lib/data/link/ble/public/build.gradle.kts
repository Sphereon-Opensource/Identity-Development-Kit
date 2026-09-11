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
                filter { isFailOnNoMatchingTests = false }
                failOnNoDiscoveredTests = false
            }
        }
    }
    androidLibrary {
        namespace = "com.sphereon.data.link.ble"
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
  /*  js {
        browser()
        nodejs()
    }*/

    configureIosTargetsIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libCoreApiPublic)
//                implementation(projects.libCoreApiDefault)

//                implementation(libs.bundles.app.platform.di)
                api(projects.libCryptoCore)
                api(projects.libCoreEventsPublic)
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.client.logging)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(sphereonlib.io.ktor.server.test.host)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(sphereonlib.io.ktor.client.cio.jvm)
                api(sphereonlib.io.ktor.client.okhttp.jvm)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCryptoCore)
                implementation(projects.libCryptoCorePublic)
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
                implementation(projects.libCryptoCorePublic)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCoreImpl)

                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.androidx.test.rules)
                implementation(kotlin("test"))
                implementation(sphereonlib.androidx.test.ext.junit.ktx)
                implementation(sphereonlib.androidx.test.espresso.core)
            }
        }

      /*  val jsMain by getting {
            dependencies {
                api(sphereonlib.io.ktor.client.cio.js)
            }
        }

        val jsTest by getting {
            dependencies {
            }
        }*/
    }
}
