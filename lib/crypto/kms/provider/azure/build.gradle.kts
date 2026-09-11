import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
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
//                failOnNoDiscoveredTests = false
            }
        }
    }
    /*  androidTarget {
          publishLibraryVariants("release")
          compilations.all {
              kotlinOptions {
                  jvmTarget = JavaVersion.VERSION_17.toString()
              }
          }
      }*/
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                nodejs {
                  /*  testTask {
                        // Configure Mocha timeout via command-line arguments
                        args("--timeout", "60000")
                    }*/
                }
                binaries.library()
                generateTypeScriptDefinitions()
            }
        }
    }
    /*
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
        hostOs == "Linux" && isArm64 -> linuxArm64("native")
        hostOs == "Linux" && !isArm64 -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }*/

    sourceSets {
        val commonMain by getting {
            dependencies {
                // default deps are already injected by convention plugin!
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(projects.libCoreApiPublic)
                implementation(projects.libCryptoCore)
                implementation(projects.libCryptoCorePublic)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(sphereonlib.org.jetbrains.kotlinx.io.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCoreTest)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.io.kotest.framework.engine)
                implementation(sphereonlib.io.kotest.assertions.core)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project.dependencies.platform(sphereonlib.com.azure.sdk.bom))
//               implementation(projects.libCryptoKmsCommonPublic)
                implementation(sphereonlib.com.azure.identity)
                implementation(sphereonlib.com.azure.security.keyvault.administration)
                implementation(sphereonlib.com.azure.security.keyvault.certificates)
                implementation(sphereonlib.com.azure.security.keyvault.keys)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.reactor)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.io.kotest.runner.junit5)
                implementation(projects.libCoreTest)
//                implementation(projects.libCoreApiDefault)
//               implementation(projects.libCryptoKmsCommonPublic)
//                implementation(projects.libCryptoKmsProviderSoftware)
            }
        }
        findByName("jsMain")?.dependencies {
            implementation(npm("@azure/identity", "4.10.0"))
            implementation(npm("@azure/keyvault-keys", "4.9.0"))
            implementation(npm("@azure/keyvault-secrets", "4.9.0"))
//               implementation(projects.libCryptoKmsCommonPublic)
        }

        findByName("jsTest")?.dependencies {
            implementation(kotlin("test"))
            implementation(sphereonlib.io.kotest.assertions.core)
            implementation(sphereonlib.io.kotest.framework.engine)
            implementation(projects.libCoreApiDefault)
            implementation(projects.libCoreTest)
            implementation(sphereonlib.io.kotest.property)
        }
        /* val nativeMain by getting {
             dependencies {}
         }
         val nativeTest by getting*/
    }
}
