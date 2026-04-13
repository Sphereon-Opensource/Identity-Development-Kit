import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    id("maven-publish")
    alias(sphereonplug.plugins.org.jetbrains.kotlinx.kover)
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
                api(projects.libCoreApiPublic)
                api(projects.libCborPublic)
                api(projects.libCryptoCorePublic)
                api(projects.libDataLinkHttpClientPublic)

                // DI dependencies for impl classes in this module
                implementation(libs.bundles.app.platform.di)

                api(sphereonlib.io.ktor.serialization.kotlinx.cbor)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.cbor)
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.optimal)
                implementation(sphereonlib.org.jetbrains.kotlinx.io.core)
                implementation(sphereonlib.co.touchlab.kermit)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCborImpl)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(projects.libCoreTest)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(sphereonlib.io.kotest.assertions.core)
                implementation(sphereonlib.io.kotest.framework.engine)
                implementation(sphereonlib.io.kotest.property)
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.io.mockk.mockk)
                // DI support for integration tests
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKeyPersistenceImpl)
                implementation(projects.libMdocCoreImpl)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        findByName("jsTest")?.dependencies {
            implementation(projects.libMdocCoreImpl)
            implementation(projects.libDataLinkHttpClientImpl)
            implementation(sphereonlib.io.kotest.assertions.core)
            implementation(sphereonlib.io.kotest.framework.engine)
            implementation(sphereonlib.io.kotest.property)
        }
        // wasmJsMain: no platform-specific config needed (created by configureWasmJsTargetIfEnabled when wasmJs target enabled)
        findByName("wasmJsTest")?.dependencies {
            implementation(projects.libMdocCoreImpl)
            implementation(projects.libDataLinkHttpClientImpl)
            implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
        }
        findByName("nativeTest")?.dependencies {
            implementation(projects.libMdocCoreImpl)
            implementation(projects.libCryptoKeyPersistenceImpl)
            implementation(projects.libDataLinkHttpClientImpl)
            implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
        }
    }
}

// Exclude kotest-framework-engine from wasmJs: it registers a duplicate 'startUnitTests'
// wasm export that conflicts with kotlin-test, causing module load failure (KT-72649).
configurations.matching { it.name.startsWith("wasmJs") && it.name.contains("Test") }.configureEach {
    exclude(group = "io.kotest", module = "kotest-framework-engine")
    exclude(group = "io.kotest", module = "kotest-framework-engine-wasm-js")
}

// Test coverage configuration
kover {
    reports {
        // Exclude test classes, generated code, and third-party DI code from coverage
        filters {
            excludes {
                classes("*Test", "*Test\$*", "*.testing.*", "*\$\$*")
                // Exclude Amazon lastmile inject library code (third-party DI infrastructure)
                classes("amazon.lastmile.inject.*", "software.amazon.lastmile.kotlin.inject.*")
                // Exclude generated component implementations
                classes("*.create\$*", "*\$Companion\$create\$*")
                classes("*\$\$InjectClass", "*\$\$InjectModule")
                // Exclude Kotlin interface default method implementations
                classes("**\$DefaultImpls", "**\$DefaultImpls\$*")
            }
        }

        total {
            verify {
                onCheck = false
                rule("Minimum line coverage") {
                    minBound(90)
                }
                rule("Minimum branch coverage") {
                    minBound(80)
                }
            }
        }
    }
}
