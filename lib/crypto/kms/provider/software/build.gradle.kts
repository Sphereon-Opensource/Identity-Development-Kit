import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
//    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
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
                    testTask {
                        useMocha {
                            timeout = "40000"
                        }
                    }
                }
             /*   browser {
                    testTask {
                        useMocha {
                            timeout = "40000"
                        }
                    }
                }*/

                binaries.library()
                generateTypeScriptDefinitions()
            }
        }
    }

    configureWasmJsTargetIfEnabled {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "ios" in kmpTargets) {
            iosX64 { binaries.all { linkerOpts("-framework", "Security") } }
            iosArm64 { binaries.all { linkerOpts("-framework", "Security") } }
            iosSimulatorArm64 { binaries.all { linkerOpts("-framework", "Security") } }
        }
    }
    configureLinuxTargetIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.libCoreApiPublic)
                implementation(sphereonlib.dev.whyoleg.cryptography.core)
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.optimal)
//                implementation(projects.libCoreApiDefault)
                implementation(libs.bundles.app.platform.di)

                implementation(projects.libCborPublic)
                implementation(projects.libCryptoCore)

                // Impl because we directly instantiate a memory keystore
                implementation(projects.libCryptoCoreImpl)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.cbor)
//                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.io.core)
//                implementation(sphereonlib.dev.whyoleg.cryptography.core)
                implementation(sphereonlib.at.asitplus.awesn1.core)
                implementation(sphereonlib.at.asitplus.awesn1.crypto)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoKeyPersistenceImpl)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCoreApiDefault)
            }
        }
        findByName("jsTest")?.dependencies {
//                implementation(sphereonlib.io.kotest.assertions.core)
//                implementation(sphereonlib.io.kotest.framework.engine)

//                implementation(sphereonlib.io.kotest.property)
            implementation(projects.libCoreApiDefault)
            implementation(projects.libDataLinkHttpClientImpl)
        }
        findByName("wasmJsMain")?.dependencies {
            implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
        }
        findByName("wasmJsTest")?.dependencies {
            implementation(projects.libCoreApiDefault)
            implementation(projects.libDataLinkHttpClientImpl)
        }
        /*
         * iosMain: no platform-specific config needed (created by hierarchy template when iOS targets enabled)
         *
        val iosTest by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                implementation(projects.libCoreApiDefault)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }*/

        findByName("iosX64Test")?.dependencies {
            implementation(libs.bundles.app.platform.di)
            implementation(projects.libCoreApiDefault)
            implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            implementation(sphereonlib.software.amazon.app.platform.metro.impl)
        }

        findByName("iosSimulatorArm64Test")?.dependencies {
            implementation(libs.bundles.app.platform.di)
            implementation(projects.libCoreApiDefault)
            implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            implementation(sphereonlib.software.amazon.app.platform.metro.impl)
        }
    }
}

// Bump language/API version to 2.2 for @JsFun support in wasmJs AES-KW implementation
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}
