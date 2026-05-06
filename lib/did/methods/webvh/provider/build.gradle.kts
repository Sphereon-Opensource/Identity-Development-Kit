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

import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
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
                nodejs()
                binaries.library()
                generateTypeScriptDefinitions()
            }
        }
    }
    configureWasmJsTargetIfEnabled {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    configureIosTargetsIfEnabled()
    configureLinuxTargetIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libDidMethodsWebvhPublic)
                api(projects.libDidMethodsWebvhResolver)
                api(projects.libDidManagerPublic)
                api(projects.libCryptoCore)
                api(projects.libCryptoDataIntegrityProofPublic)
                implementation(projects.libCryptoDataIntegrityProofEddsaJcs2022)
                api(projects.libDidMethodsKey)
                api(projects.libCoreApiPublic)
                api(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                api(sphereonlib.org.jetbrains.kotlinx.datetime)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.core)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)

                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                // Test classpath is shared across every target so that the
                // expect/actual `createWebvhProviderTestAppGraph` Metro graph
                // resolves the same set of `@ContributesBinding` impls on JVM,
                // JS, wasmJs, iOS, and Linux native test runners.
                implementation(projects.libCoreTest)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libCryptoDataIntegrityProofImpl)
                implementation(projects.libCryptoDataIntegrityProofEddsaJcs2022)
                implementation(projects.libDidResolverImpl)
                implementation(projects.libDidMethodsKey)
                implementation(projects.libDidPersistenceMemory)
                implementation(projects.libDataLinkHttpClientImpl)
                // The provider test graphs include :resolver, which now wires
                // `did.webvh.validate-trust` → `trust.did.validate`. The Metro
                // graph needs the TrustConfigProvider binding from
                // trust-core-impl.
                implementation(projects.libTrustCoreImpl)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(sphereonlib.io.ktor.client.mock)
            }
        }
    }
}
