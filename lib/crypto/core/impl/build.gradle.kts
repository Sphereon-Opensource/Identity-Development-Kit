import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.npm.publish.org.jetbrains.kotlin.npm.publish.gradle.plugin)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(libs.plugins.metro)
    id("maven-publish")
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xenable-suspend-function-exporting")
                }
            }
        }
    }
    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }
    js {
        outputModuleName = "@sphereon/kmp-lib-crypto-core-impl"
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

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Public API
                api(projects.libCryptoCorePublic)

                // DI dependencies
                implementation(libs.bundles.app.platform.di)
                api(projects.libCoreApiDefault)

                // Dependencies from kms-common-impl
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.cbor)
                implementation(sphereonlib.dev.whyoleg.cryptography.core)
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.optimal)
                implementation(sphereonlib.org.jetbrains.kotlinx.io.core)
                implementation(sphereonlib.at.asitplus.signum.indispensable)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.org.kotlincrypto.core.digest)
                implementation(sphereonlib.org.kotlincrypto.hash.sha1)
                implementation(sphereonlib.org.kotlincrypto.hash.sha2)
                implementation(libs.amz.metro.impl)
                // Dependencies from kms-common-impl tests
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCoreTest)
                implementation(projects.libCryptoKmsProviderSoftware)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.jdk)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.io.ktor.client.cio.jvm)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(libs.mockk)
                implementation(sphereonlib.io.ktor.client.mock)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("@js-joda/core", "5.6.3"))
                implementation(npm("@js-joda/timezone", "2.22.0"))
                implementation(libs.amz.metro.impl)
                api(sphereonlib.dev.whyoleg.cryptography.provider.webcrypto)
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(sphereonlib.io.kotest.assertions.core)
                implementation(sphereonlib.io.kotest.framework.engine)
                implementation(sphereonlib.io.kotest.property)
                implementation(sphereonlib.io.ktor.client.core.js)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.amz.metro.impl)
                api(sphereonlib.dev.whyoleg.cryptography.provider.webcrypto)
            }
        }
        val wasmJsTest by getting {
            dependencies {
                implementation(sphereonlib.io.kotest.assertions.core)
                implementation(sphereonlib.io.kotest.property)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val appleMain by getting {
            dependencies {
                api(sphereonlib.io.ktor.client.darwin)
            }
        }
    }
}

// Exclude kotest-framework-engine from wasmJs: it registers a duplicate 'startUnitTests'
// wasm export that conflicts with kotlin-test, causing module load failure (KT-72649).
configurations.matching { it.name.startsWith("wasmJs") && it.name.contains("Test") }.configureEach {
    exclude(group = "io.kotest", module = "kotest-framework-engine")
    exclude(group = "io.kotest", module = "kotest-framework-engine-wasm-js")
}

npmPublish {
    registries {
        register("npmjs") {
            uri.set("https://registry.npmjs.org")
            authToken.set(System.getenv("NPM_TOKEN") ?: "")
        }
    }
    packages {
        named("js") {
            packageJson {
                "name" by "@sphereon/kmp-crypto-core-impl"
                "version" by rootProject.extra["npmVersion"] as String
            }
            scope.set("@sphereon")
            packageName.set("kmp-crypto-core-impl")
        }
    }
}

// Replace wasmJs npm-publish tasks: mainFile provider has no value on Kotlin 2.3.x wasmJs targets
afterEvaluate {
    listOf("assembleWasmJsPackage", "packWasmJsPackage", "publishWasmJsPackageToNpmjsRegistry").forEach { taskName ->
        try { tasks.replace(taskName) } catch (_: Exception) {}
    }
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
                // Exclude classes with unreachable defensive else branches
                // (IdentifierOptsOrResult only has 2 implementations: Managed and External)
                classes(
                    "com.sphereon.crypto.resolution.MultiIdentifierResolutionServiceImpl",
                    "com.sphereon.crypto.resolution.MultiIdentifierResolutionServiceImpl\$*"
                )
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
