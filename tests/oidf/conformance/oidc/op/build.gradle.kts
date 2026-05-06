/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * OIDF conformance OP harness module.
 *
 * Boots the IDK OAuth2 Authorization Server end to end so the OpenID Foundation
 * conformance suite can drive Basic-OP profile tests against it. The harness is
 * pure IDK: no EDK or VDX modules, no commercial features. The `checkIdkPurity`
 * task at the bottom of this file fails the build if any `com.sphereon.edk` or
 * `com.sphereon.vdx` artifact sneaks onto the runtime or compile classpath.
 *
 * The harness is JVM-only because it boots a Ktor Netty server, but every
 * piece of IDK code it consumes (config-backed user provider, branded login
 * renderer, AS REST adapters, KMS) lives in commonMain and stays multiplatform.
 */
plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}

metro {
}

kotlin {
    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                // OAuth2 common models
                implementation(projects.libOauth2CommonPublic)
                implementation(projects.libOauth2CommonImpl)

                // OAuth2 Authorization Server (OP) impl + REST adapters
                implementation(projects.libOauth2ServerAuthorizationPublic)
                implementation(projects.libOauth2ServerAuthorizationImpl)
                implementation(projects.servicesOauth2AsRest)

                // OAuth2 Resource Server (for `/userinfo` access token verification)
                implementation(projects.libOauth2ServerResourcePublic)
                implementation(projects.libOauth2ServerResourceImpl)

                // OAuth2 client (RP-side helpers, also keeps the session graph complete)
                implementation(projects.libOauth2ClientPublic)
                implementation(projects.libOauth2ClientImpl)

                // Core API + defaults (config service, classpath properties source, root scope)
                implementation(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)

                // Events (audit hooks the AS commands emit)
                implementation(projects.libCoreEventsImpl)

                // Crypto core + Software KMS (in-process signing keys for the OP)
                implementation(projects.libCryptoCorePublic)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoCore)
                implementation(projects.libCryptoKmsProviderSoftware)

                // HTTP client (used internally by the AS for federation / introspection wiring)
                implementation(projects.libDataLinkHttpClientPublic)
                implementation(projects.libDataLinkHttpClientImpl)

                // Ktor server runtime + KotlinInject plugin for HttpAdapter dispatch
                implementation(projects.ktorServerKotlinInject)
                implementation(sphereonlib.io.ktor.server.core)
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.server.content.negotiation)
                implementation(sphereonlib.io.ktor.server.status.pages)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)

                // Serialization + coroutines
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                // DI bundle
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)

                // Ktor client to drive the embedded server in tests
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.cio)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)

                // mTLS conformance fixture: Ktor Netty engine + Bouncy Castle for in-process TLS
                // termination with `setNeedClientAuth(true)` and ad-hoc CA / leaf cert generation.
                // Netty is used (not CIO) because Ktor's CIO server engine has limited mTLS
                // surface; Netty's pipeline exposes the SslHandler we need to populate
                // ClientCertificateChainAttributeKey on each call.
                implementation(sphereonlib.io.ktor.server.netty)
                implementation(sphereonlib.org.bouncycastle.bcprov.jdk18on)
                implementation(sphereonlib.org.bouncycastle.bcpkix.jdk18on)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// runOidfOp task — boots OidfOpApplication.main() under Gradle. The OIDF
// conformance suite points its OP under test at http://localhost:8080.
// ---------------------------------------------------------------------------
val runOidfOp by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Boots the OIDF conformance OP harness on http://localhost:8080."
    mainClass.set("com.sphereon.oauth2.oidf.op.OidfOpApplicationKt")
    val jvmRuntimeClasspath = configurations.named("jvmRuntimeClasspath")
    val jvmMainOutput =
        kotlin
            .jvm()
            .compilations
            .named("main")
            .map { it.output.allOutputs }
    classpath(jvmMainOutput, jvmRuntimeClasspath)
    standardInput = System.`in`
}

// ---------------------------------------------------------------------------
// Inline IDK purity check.
//
// Fails the build if any resolved artifact on the JVM compile or runtime
// classpath belongs to "com.sphereon.edk" or "com.sphereon.vdx". This guards
// the conformance harness against accidentally pulling commercial code through
// a transitive dependency. Third-party groups (Ktor, kotlinx) are fine.
//
// Both configurations are resolved at configuration time so the doLast body
// stays configuration-cache compatible (Gradle 9 forbids resolving
// Configuration objects at task execution time).
// ---------------------------------------------------------------------------
val compileArtifactIds: Provider<List<String>> =
    provider {
        configurations
            .named("jvmCompileClasspath")
            .get()
            .resolvedConfiguration
            .resolvedArtifacts
            .map { "jvmCompileClasspath:${it.moduleVersion.id}" }
    }

val runtimeArtifactIds: Provider<List<String>> =
    provider {
        configurations
            .named("jvmRuntimeClasspath")
            .get()
            .resolvedConfiguration
            .resolvedArtifacts
            .map { "jvmRuntimeClasspath:${it.moduleVersion.id}" }
    }

val checkIdkPurity by tasks.registering {
    group = "verification"
    description = "Fails if any com.sphereon.edk or com.sphereon.vdx artifact is on the harness classpath."

    val compileIds = compileArtifactIds
    val runtimeIds = runtimeArtifactIds
    doLast {
        val ids = compileIds.get() + runtimeIds.get()
        val offending =
            ids.filter { id ->
                id.contains(":com.sphereon.edk:") || id.contains(":com.sphereon.vdx:")
            }
        if (offending.isNotEmpty()) {
            val list = offending.joinToString("\n") { "  - $it" }
            throw GradleException(
                "IDK purity violated for tests-oidf-conformance-oidc-op:\n$list",
            )
        }
        logger.lifecycle("checkIdkPurity: OK (${ids.size} artifacts scanned across compile + runtime classpaths)")
    }
}

tasks.named("check") {
    dependsOn(checkIdkPurity)
}
