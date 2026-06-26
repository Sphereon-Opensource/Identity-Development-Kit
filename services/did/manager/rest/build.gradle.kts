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
 * DID Manager REST API Server.
 *
 * Provides the IDK-21 OpenAPI HTTP surface for managing DIDs: creating, updating, importing,
 * resolving, and deactivating DIDs plus sub-resources (verification methods, services,
 * controllers, aliases). The server is framework-agnostic — any host (Ktor, Spring, serverless)
 * can mount `DidManagerHttpAdapter`.
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
                api(projects.libCryptoCorePublic)
                api(projects.libDidCorePublic)
                api(projects.libDidManagerPublic)
                // ServiceCommand interfaces (DidIdInput, *AlsoKnownAsServiceCommand, etc.)
                // currently live in lib-did-manager-impl/commonMain (package com.sphereon.did.manager.command).
                api(projects.libDidManagerImpl)
                api(projects.libDidPersistenceApi)
                api(projects.libDidResolverPublic)
                api(projects.libCoreApiPublic)
                api(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.core)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
            }
        }
        // NOTE: jvmMain intentionally has NO sources and NO dependencies. This library's published
        // surface is the framework-agnostic DidManagerHttpAdapter (commonMain), which depends only
        // on the persistence .api/.public contracts. The standalone dev Ktor server
        // (DidManagerKtorServer) — which composes a full Metro @DependencyGraph and therefore must
        // pin a concrete persistence dialect at compile time — lives in jvmTest. Final applications
        // bring the host + the concrete dialect (postgres/sqlite/mysql/memory); this library never
        // forces one on its consumers.
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test.junit5)
                // Standalone dev/test Ktor server (DidManagerKtorServer, moved here from jvmMain) +
                // its full Metro graph. The graph composes at compile time, so it needs a concrete
                // persistence dialect on the TEST classpath; this never reaches consumers.
                implementation(projects.ktorServerKotlinInject)
                implementation(sphereonlib.io.ktor.server.core)
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.server.content.negotiation)
                implementation(sphereonlib.io.ktor.server.status.pages)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libDidManagerImpl)
                implementation(projects.libDidResolverImpl)
                implementation(projects.libDidMethodsKey)
                implementation(projects.libDidMethodsJwk)
                implementation(projects.libDidMethodsWeb)
                // Concrete persistence dialects (key + DID stores) for the test server's graph.
                implementation(projects.libCryptoKeyPersistenceSqlite)
                implementation(projects.libDidPersistenceMemory)
                // Ktor test host + client for the adapter E2E tests.
                implementation(sphereonlib.io.ktor.server.test.host)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
            }
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
