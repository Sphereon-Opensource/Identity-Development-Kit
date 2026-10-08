/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import co.touchlab.skie.configuration.SealedInterop
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    id("co.touchlab.skie") version "0.10.11"
    id("maven-publish")
}
metro {

}

skie {
    features {

        // Disable enum/sealed class wrapping for Indispensable due to Asn1Element/Asn1Sequence type hierarchy issues
        group("at.asitplus.signum.indispensable") {
            coroutinesInterop.set(false)
            SealedInterop.Enabled(false)
        }
        group("at.asitplus.signum") {
            coroutinesInterop.set(false)
            SealedInterop.Enabled(false)
        }

        group("Indispensable") {
            coroutinesInterop.set(false)
            SealedInterop.Enabled(false)
        }
    }
}

val xcFramework = XCFramework("Idk")

kotlin {
    androidLibrary {
        namespace = "com.sphereon.idk.all"
        compileSdk = 35
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjdk-release=${JavaVersion.VERSION_17}")
        }
    }

  /*  jvm {
        compilations.all {
            compileTaskProvider {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                    freeCompilerArgs.add("-Xjdk-release=${JavaVersion.VERSION_17}")
                }
            }
        }
    }*/

   /* js {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }*/

    // iOS targets for XCFramework — gated by kmp.targets
    val iosTargets = run {
        val enabledTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in enabledTargets || "ios" in enabledTargets) listOf(iosArm64(), iosSimulatorArm64())
        else emptyList()
    }
    iosTargets.forEach { iosTarget ->
        /*iosTarget.compilations.getByName("main") {
            compileTaskProvider.configure {
                compilerOptions {
                    // Use short Objective-C names without package prefixes for cleaner Swift interop
                    freeCompilerArgs.addAll(
                        "-Xobjc-generics",
                        "-module-name", "Idk"
                    )
                }
            }
        }*/

        iosTarget.binaries.framework {
            baseName = "Idk"
            isStatic = false
            xcFramework.add(this)
            export(projects.libCbor)
            export(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-default:$version" else project(":lib-core-api-default"))
            export(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-public:$version" else project(":lib-core-api-public"))
            export(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-loggers-mobile-logger:$version" else project(":lib-core-loggers-mobile-logger"))
//            export(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core:$version" else project(":lib-crypto-core"))
            export(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core-public:$version" else project(":lib-crypto-core-public"))
            export(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core-impl:$version" else project(":lib-crypto-core-impl"))
//            export(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-kms-provider-aws:$version" else project(":lib-crypto-kms-provider-aws"))
//            export(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-kms-provider-azure:$version" else project(":lib-crypto-kms-provider-azure"))
//            export(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-kms-provider-mobile:$version" else project(":lib-crypto-kms-provider-mobile"))
//            export(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-kms-provider-software:$version" else project(":lib-crypto-kms-provider-software"))
            export(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-ble-public:$version" else project(":lib-data-link-ble-public"))
            export(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-nfc-impl:$version" else project(":lib-data-link-nfc-impl"))
            export(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-nfc-public:$version" else project(":lib-data-link-nfc-public"))
            export(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-http-client-impl:$version" else project(":lib-data-link-http-client-impl"))
            export(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-http-client-public:$version" else project(":lib-data-link-http-client-public"))
            export(projects.libMdocCoreImpl)
            export(projects.libMdocCorePublic)
            export(projects.libMdocDatatransferImpl)
            export(projects.libMdocDatatransferPublic)
            transitiveExport = false
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndroid") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                // dependencies for DI
                implementation(libs.bundles.app.platform.di)

                // All library dependencies
                api(projects.libCbor)
                api(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-default:$version" else project(":lib-core-api-default"))
                api(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-loggers-mobile-logger:$version" else project(":lib-core-loggers-mobile-logger"))
                api(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core-impl:$version" else project(":lib-crypto-core-impl"))
                api(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-kms-provider-software:$version" else project(":lib-crypto-kms-provider-software"))
                api(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-ble-public:$version" else project(":lib-data-link-ble-public"))
                api(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-nfc-impl:$version" else project(":lib-data-link-nfc-impl"))
                api(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-http-client-impl:$version" else project(":lib-data-link-http-client-impl"))
                api(projects.libMdocCoreImpl)
                api(projects.libMdocDatatransferImpl)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        androidMain {
            dependencies {
            }
        }

       /* jvmMain {
            dependencies {
            }
        }*/
    }
}

