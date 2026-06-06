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
        val jvmMain by getting {
            dependencies {
                // Ktor server
                implementation(projects.ktorServerKotlinInject)
                implementation(sphereonlib.io.ktor.server.core)
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.server.content.negotiation)
                implementation(sphereonlib.io.ktor.server.status.pages)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)

                // Concrete implementations that satisfy the Metro DI graph at runtime. The
                // module can still be consumed without these (e.g., host apps that wire their
                // own impls) — they are attached here so the bundled Ktor entrypoint has a
                // working dev-friendly graph out of the box.
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                // SQLite-backed KeyReferenceStore. SqliteKeyReferenceStoreImpl carries
                // @ContributesBinding(SessionScope, replaces=[NoOpKeyReferenceStore]), so as long
                // as this dependency is present at the graph-composition compile-time (jvmMain),
                // KSP picks it up over the NoOp default. Required for did:key / did:jwk
                // creation to find a working keyref store; without it findKeyReferenceId aborts
                // MANAGED VM creation with "key-reference store is unavailable". Resolves the
                // VDX-infra-otp E2E test skips and the corresponding production-server gap.
                implementation(projects.libCryptoKeyPersistenceSqlite)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libDidManagerImpl)
                implementation(projects.libDidResolverImpl)
                implementation(projects.libDidPersistenceMemory)
                implementation(projects.libDidMethodsKey)
                implementation(projects.libDidMethodsJwk)
                implementation(projects.libDidMethodsWeb)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test.junit5)
                // Compile-time access to the impl-only Session graphs (DidManagerServiceImpl.Graph,
                // DidCreationDslProcessor.Graph) so REST E2E tests can mint fixture DIDs through
                // the DSL instead of the OpenAPI surface they're meant to exercise.
                implementation(projects.libDidManagerImpl)
                // Ktor test host for VDX-infra-ubb — boots an in-process server with the real
                // KotlinInjectPlugin so we can verify X-Tenant-ID / X-User-ID header forwarding
                // through the full chain into endpoint command execution context.
                implementation(sphereonlib.io.ktor.server.test.host)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
            }
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
