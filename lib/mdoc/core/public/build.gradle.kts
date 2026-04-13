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
    js(IR) {
        outputModuleName = "@sphereon/kmp-mdoc-core-public"
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
                api(projects.libCoreApiPublic)
                api(projects.libCoreCompat)
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
                implementation(libs.amz.metro.impl)
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
                implementation(libs.mockk)
                // DI support for integration tests
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libMdocCoreImpl)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jsMain by getting {
            dependencies {
                api(projects.libCoreCompat)
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(projects.libMdocCoreImpl)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(sphereonlib.io.kotest.assertions.core)
                implementation(sphereonlib.io.kotest.framework.engine)
                implementation(sphereonlib.io.kotest.property)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                api(projects.libCoreCompat)
            }
        }
        val wasmJsTest by getting {
            dependencies {
                implementation(projects.libMdocCoreImpl)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
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
                "name" by "@sphereon/kmp-mdoc-core-public"
                "version" by rootProject.extra["npmVersion"] as String
            }
            scope.set("@sphereon")
            packageName.set("kmp-mdoc-core-public")
        }
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

// Replace wasmJs npm-publish tasks: mainFile provider has no value on Kotlin 2.3.x wasmJs targets
afterEvaluate {
    listOf("assembleWasmJsPackage", "packWasmJsPackage", "publishWasmJsPackageToNpmjsRegistry").forEach { taskName ->
        try { tasks.replace(taskName) } catch (_: Exception) {}
    }
}
