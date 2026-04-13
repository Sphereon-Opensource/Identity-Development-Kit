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
 * Universal DID Resolver REST API Server.
 *
 * This module provides a DIF Universal Resolver compatible REST API
 * for resolving DIDs to DID Documents.
 *
 * Endpoints:
 * - GET /1.0/identifiers/{did} - Resolve a DID
 * - GET /1.0/methods - List supported methods
 * - GET /1.0/properties - Get resolver properties
 *
 * @see <a href="https://w3c-ccg.github.io/did-resolution/">DID Resolution Spec</a>
 * @see <a href="https://github.com/decentralized-identity/universal-resolver">DIF Universal Resolver</a>
 */

import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

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
                compilerOptions {
                    moduleKind = JsModuleKind.MODULE_ES
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

    configureWasmJsTargetIfEnabled {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // DID modules
                api(projects.libDidCorePublic)
                api(projects.libDidResolverPublic)
                api(projects.libDidResolverImpl)
                api(projects.libDidManagerPublic)

                // Core API
                api(projects.libCoreApiPublic)
                api(projects.libDataLinkHttpClientPublic)

                // Kotlin
                api(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.core)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)

                // DI
                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}
