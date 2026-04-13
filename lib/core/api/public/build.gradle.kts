import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    // Fixes the multi-publish variant issue
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.metro)
    alias(sphereonplug.plugins.org.jetbrains.kotlinx.atomicfu)
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
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
    /*androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }*/
    js {
/*        browser {
            useEsModules()
            binaries.executable()
            generateTypeScriptDefinitions()

        }*/
        compilerOptions {
            moduleKind = JsModuleKind.MODULE_ES
            target = "es2015"
            freeCompilerArgs.add("-Xes-long-as-bigint")
        }
        nodejs {
            useEsModules()
            binaries.executable()
            generateTypeScriptDefinitions()
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.skie.configuration.annotations)
                implementation(sphereonlib.com.michael.bull.kotlin.result.kotlin.result)
                api(projects.libCoreCompat)
                // default deps are already injected by conventions plugin!
                api(sphereonlib.org.jetbrains.kotlinx.io.core)
                api(libs.bundles.app.platform.di)
                // Needed for the coroutine context
                api(libs.amz.metro.public)
                api(libs.amz.metro.impl)
                // HTTP client interfaces used across modules (implementations are supplied by host applications)
                api(sphereonlib.io.ktor.client.core)
                api(sphereonlib.io.ktor.client.content.negotiation)
                api(sphereonlib.io.ktor.client.logging)
                api(sphereonlib.io.ktor.serialization.kotlinx.json)

            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(libs.amz.metro.impl)
            }
        }

        // Tests that depend on lib-core-api-default and lib-core-test
        // (shared by jvm, js, native tests; wasmJs has its own test source set)
        val nonWasmTest by creating {
            dependsOn(commonTest)
            dependencies {
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCoreTest)
            }
        }
        val jvmTest by getting {
            dependsOn(nonWasmTest)
        }
        val jsTest by getting {
            dependsOn(nonWasmTest)
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core.js)
            }
        }
        val nativeTest by getting {
            dependsOn(nonWasmTest)
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
                // Exclude documentation/example code (not production code)
                classes("*Example", "*Example\$*")
                // Exclude Kotlin interface default method implementations (compiled as $DefaultImpls)
                // Using ** to match across package/class segments
                classes("**\$DefaultImpls", "**\$DefaultImpls\$*")
                // Exclude DI scope marker classes (abstract with private constructor, no testable behavior)
                classes("com.sphereon.di.session.SessionScope")
                // Exclude data classes marked with @CoverageExcludedDataClass (generated methods create excessive branches)
                annotatedBy("com.sphereon.core.api.conf.CoverageExcludedDataClass")
            }
        }

        total {
            verify {
                onCheck = false
                rule("Module minimum line coverage") {
                    minBound(80)
                }
            }
        }
    }
}
