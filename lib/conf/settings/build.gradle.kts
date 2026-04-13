import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.metro)
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
//    configureCommonMainKsp()
    jvm()

    androidLibrary {
        namespace = "com.sphereon.config.multiplatform"
        compileSdk = 35
        minSdk = 27
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    js(IR) {
        browser()
        nodejs()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()


    sourceSets {
        val commonMain by getting {
            dependencies {
                // default deps are already injected by conventions plugin!
                implementation(sphereonlib.org.jetbrains.kotlinx.io.core)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.com.russhwolf.multiplatform.settings)
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
        val androidMain by getting {
            dependencies {
                implementation(sphereonlib.com.russhwolf.multiplatform.settings.datastore)
                implementation(sphereonlib.com.russhwolf.multiplatform.settings.coroutines)
                implementation(sphereonlib.androidx.startup.runtime)
                implementation(sphereonlib.androidx.datastore.preferences.core)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(sphereonlib.com.russhwolf.multiplatform.settings)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(sphereonlib.com.russhwolf.multiplatform.settings)
            }
        }

        val linuxMain by getting { }
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
                    minBound(70)  // Settings module has platform-specific code
                }
            }
        }
    }
}
