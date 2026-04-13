import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
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
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                nodejs {
                    binaries.library()
                    generateTypeScriptDefinitions()
                }
            }
        }
    }
    configureWasmJsTargetIfEnabled {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    configureIosTargetsIfEnabled()
    configureLinuxTargetIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                api(projects.libTrustCorePublic)
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCore)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.org.kotlincrypto.hash.sha1)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libTrustDid)
                implementation(projects.libTrustEtsi)
                implementation(projects.libTrustX509)
                implementation(projects.libDidResolverImpl)
                implementation(projects.libDidMethodsKey)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.io.ktor.client.cio.jvm)
            }
        }
        findByName("jsMain")?.dependencies {
            implementation(sphereonlib.software.amazon.app.platform.metro.impl)
        }
        findByName("jsTest")?.dependencies {
            implementation(sphereonlib.io.kotest.assertions.core)
            implementation(sphereonlib.io.kotest.framework.engine)
            implementation(sphereonlib.io.kotest.property)
        }
        findByName("appleMain")?.dependencies {
            api(sphereonlib.io.ktor.client.darwin)
        }
    }
}
