import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
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
    /*js {
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

/*
        val jsMain by getting {
            dependencies {
                api(sphereonlib.io.ktor.client.cio.js)
            }
        }

        val jsTest by getting {
            dependencies {
            }
        }
*/
    }
}
