import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
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

    configureIosTargetsIfEnabled()

    configureWasmJsTargetIfEnabled {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    configureLinuxTargetIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Core dependencies (no DI)
                api(projects.libCoreApiPublic)
                api(projects.libCborPublic)
                api(sphereonlib.dev.whyoleg.cryptography.core)
                api(sphereonlib.dev.whyoleg.cryptography.provider.optimal)
                implementation(sphereonlib.org.kotlincrypto.core.digest)
                implementation(sphereonlib.org.kotlincrypto.hash.sha1)
                implementation(sphereonlib.org.kotlincrypto.hash.sha2)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.cbor)
                implementation(sphereonlib.org.jetbrains.kotlinx.io.core)
                api(sphereonlib.at.asitplus.awesn1.core)
                api(sphereonlib.at.asitplus.awesn1.crypto)
                implementation(sphereonlib.io.ktor.client.core)
                // DI annotations
                implementation(libs.bundles.app.platform.di)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.jdk)
            }
        }
        findByName("jsMain")?.dependencies {
            implementation(npm("@js-joda/core", "5.6.3"))
            implementation(npm("@js-joda/timezone", "2.22.0"))
            api(sphereonlib.dev.whyoleg.cryptography.provider.webcrypto)
        }
        findByName("wasmJsMain")?.dependencies {
            api(sphereonlib.dev.whyoleg.cryptography.provider.webcrypto)
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCborImpl)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        findByName("appleMain")?.dependencies {
            api(sphereonlib.io.ktor.client.darwin)
        }
    }
}
