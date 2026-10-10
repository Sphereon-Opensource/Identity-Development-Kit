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
                api(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-compat-annotations:$version" else project(":lib-core-compat-annotations"))
                // default deps are already injected by convention plugin!
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-public:$version" else project(":lib-core-api-public"))
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core:$version" else project(":lib-crypto-core"))
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core-public:$version" else project(":lib-crypto-core-public"))
                implementation(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-http-client-impl:$version" else project(":lib-data-link-http-client-impl"))
                implementation(sphereonlib.org.jetbrains.kotlinx.io.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-default:$version" else project(":lib-core-api-default"))
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-kms-provider-software:$version" else project(":lib-crypto-kms-provider-software"))
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core-impl:$version" else project(":lib-crypto-core-impl"))
                implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-test:$version" else project(":lib-core-test"))
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
                implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-test:$version" else project(":lib-core-test"))
//                implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-default:$version" else project(":lib-core-api-default"))
//               implementation(projects.libCryptoKmsCommonPublic)
//                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-kms-provider-software:$version" else project(":lib-crypto-kms-provider-software"))
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
            implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-default:$version" else project(":lib-core-api-default"))
            implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-test:$version" else project(":lib-core-test"))
            implementation(sphereonlib.io.kotest.property)
        }
        /* val nativeMain by getting {
             dependencies {}
         }
         val nativeTest by getting*/
    }
}
