import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled

plugins {
//    alias(libs.plugins.android.library)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.org.jetbrains.kotlinx.atomicfu)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.org.jetbrains.kotlinx.kover)
    id("maven-publish")
}
metro {
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()

    jvm()
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                browser { testTask { enabled = false } }
                nodejs { testTask { useMocha { timeout = "60000" } } }
                binaries.library()
                generateTypeScriptDefinitions()
            }
        }
    }
    configureIosTargetsIfEnabled()
    configureLinuxTargetIfEnabled()

    configureWasmJsTargetIfEnabled {
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
                // CSPRNG backing for DefaultSecureRandom (OAuth2/OIDC token entropy source)
                implementation(sphereonlib.dev.whyoleg.cryptography.random)
                // AES-GCM for AesGcmEncryptionService (the default EncryptionService impl).
                implementation(sphereonlib.dev.whyoleg.cryptography.core)
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.optimal)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
        // Kache doesn't support wasmJs, so KacheCacheBackend lives in nonWasmMain
        val nonWasmMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(sphereonlib.com.mayakapps.kache.kache)
            }
        }
        val jvmMain by getting { dependsOn(nonWasmMain) }
        findByName("jsMain")?.dependsOn(nonWasmMain)
        findByName("nativeMain")?.dependsOn(nonWasmMain)

        // Tests that depend on KacheCacheBackend (non-wasmJs only)
        val nonWasmTest by creating {
            dependsOn(commonTest)
        }
        val jvmTest by getting { dependsOn(nonWasmTest) }
        findByName("jsTest")?.apply {
            dependsOn(nonWasmTest)
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core.js)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test.js)
            }
        }
        findByName("nativeTest")?.dependsOn(nonWasmTest)
        findByName("wasmJsTest")?.dependencies {
            implementation(sphereonlib.org.jetbrains.kotlin.test)
            implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
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
                    "com.sphereon.di.session.SessionScope",
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
