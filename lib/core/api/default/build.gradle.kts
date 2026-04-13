@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
//    alias(libs.plugins.android.library)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.org.jetbrains.kotlinx.atomicfu)
    alias(libs.plugins.metro)
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
    debug.set(true)
    reportsDestination.set(layout.buildDirectory.dir("metro-reports"))
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

    jvm()
    js {
        /*   browser {
               useEsModules()
               binaries.executable()
               generateTypeScriptDefinitions()

           }*/
        nodejs {
            useEsModules()
            binaries.library()
            generateTypeScriptDefinitions()
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    wasmJs {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // default deps are already injected by conventions plugin!
                implementation(libs.bundles.app.platform.di)
                api(projects.libCoreApiPublic)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(libs.amz.metro.impl)
            }
        }
        // Kache doesn't support wasmJs, so KacheCacheBackend lives in nonWasmMain
        val nonWasmMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.kache)
            }
        }
        val jvmMain by getting { dependsOn(nonWasmMain) }
        val jsMain by getting { dependsOn(nonWasmMain) }
        val nativeMain by getting { dependsOn(nonWasmMain) }

        // Tests that depend on KacheCacheBackend (non-wasmJs only)
        val nonWasmTest by creating {
            dependsOn(commonTest)
        }
        val jvmTest by getting { dependsOn(nonWasmTest) }
        val jsTest by getting {
            dependsOn(nonWasmTest)
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core.js)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test.js)
            }
        }
        val nativeTest by getting { dependsOn(nonWasmTest) }
        val wasmJsTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}

// Test coverage configuration
kover {
    reports {
        // Exclude test classes, generated code, and third-party DI code from coverage
        filters {
            excludes {
                // Exclude test classes and testing utilities
                classes(
                    "*Test",
                    "*Test\$*",
                    "*.testing.*",
                    "*\$\$*",
                    // Exclude Amazon lastmile inject library code (third-party DI infrastructure)
                    "amazon.lastmile.inject.*",
                    "software.amazon.lastmile.kotlin.inject.*",
                    "*ComponentMerged\$*",
                    // Exclude generated component implementations
                    "*.create\$*",
                    "*\$Companion\$create\$*",
                    "*\$\$InjectClass",
                    "*\$\$InjectModule",
                    // Exclude documentation/example code (not production code)
                    "*Example",
                    "*Example\$*",
                    // Exclude Kotlin interface default method implementations (compiled as $DefaultImpls)
                    "*\$DefaultImpls",
                    // Exclude DI scope marker classes (abstract with private constructor, no testable behavior)
                    "com.sphereon.di.session.SessionScope"
                )
            }
        }

        total {
            verify {
                onCheck = false
                rule("Minimum coverage") {
                    // Target: 80% line coverage
                    // Current: ~83.6%
                    // Note: Some DI-managed classes and complex edge cases are difficult to test directly
                    minBound(80)
                }
            }
        }
    }
}
