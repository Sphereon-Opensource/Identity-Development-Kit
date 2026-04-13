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
                    useEsModules()
                    binaries.library()
                    generateTypeScriptDefinitions()
                }
            }
        }
    }

    configureIosTargetsIfEnabled()

    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "wasmjs" in kmpTargets || "wasm" in kmpTargets) {
            configureWasmJsTargetIfEnabled {
                nodejs()
                binaries.library()
                generateTypeScriptDefinitions()
            }
        }
    }

    configureLinuxTargetIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libCoreApiPublic)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.cbor)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCborImpl)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.io.mockk.mockk)
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
                    minBound(95, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
                }
                rule("Minimum branch coverage") {
                    minBound(85, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
                }
            }
        }
    }
}
