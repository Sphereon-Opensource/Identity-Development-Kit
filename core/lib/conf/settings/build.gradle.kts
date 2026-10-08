import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.org.jetbrains.kotlinx.kover)
    id("maven-publish")
}
metro {
}

kotlin {
//    configureCommonMainKsp()
    jvm()

    androidLibrary {
        namespace = "com.sphereon.config.multiplatform"
        compileSdk = 35
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                compilerOptions {
                    moduleKind = org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_ES
                    target = "es2015"
                }
                browser { testTask { enabled = false } }
                nodejs { testTask { useMocha { timeout = "60000" } } }
                binaries.library()
                generateTypeScriptDefinitions()
            }
        }
    }
    configureIosTargetsIfEnabled()
    configureLinuxTargetIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // default deps are already injected by conventions plugin!
                implementation(sphereonlib.org.jetbrains.kotlinx.io.core)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.com.russhwolf.multiplatform.settings)
                implementation(sphereonlib.org.jetbrains.kotlinx.atomicfu)
                implementation(sphereonlib.org.kotlincrypto.core.digest)
                implementation(sphereonlib.org.kotlincrypto.hash.sha2)
                implementation(projects.libCoreApiPublic)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(projects.libCoreApiDefault)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(sphereonlib.com.russhwolf.multiplatform.settings)
            }
        }
        findByName("androidMain")?.dependencies {
            implementation(sphereonlib.com.russhwolf.multiplatform.settings.datastore)
            implementation(sphereonlib.com.russhwolf.multiplatform.settings.coroutines)
            implementation(sphereonlib.androidx.startup.runtime)
            implementation(sphereonlib.androidx.datastore.preferences.core)
        }
        findByName("jsMain")?.dependencies {
            implementation(sphereonlib.com.russhwolf.multiplatform.settings)
        }
        findByName("appleMain")?.dependencies {
            implementation(sphereonlib.com.russhwolf.multiplatform.settings)
        }

        findByName("linuxMain")?.apply { }
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
                    minBound(70) // Settings module has platform-specific code
                }
            }
        }
    }
}
