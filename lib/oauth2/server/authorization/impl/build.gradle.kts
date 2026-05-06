import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}
metro {
}

kotlin {
    applyDefaultHierarchyTemplate()
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
    configureWasmJsTargetIfEnabled {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    configureIosTargetsIfEnabled()
    configureLinuxTargetIfEnabled()

    sourceSets {
        findByName("jsTest")?.dependencies {
            implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core.js)
        }
        val commonMain by getting {
            dependencies {
                implementation(sphereonlib.co.touchlab.skie.configuration.annotations)

                // Public API
                api(projects.libOauth2ServerAuthorizationPublic)

                // OID4VP verifier service (optional at runtime — AS works without it)
                api(projects.libOpenidOid4vpVerifierPublic)

                // OID4VCI issuer public (for CredentialIssuancePolicyResolver — optional at runtime)
                api(projects.libOpenidOid4vciIssuerPublic)

                // Core dependencies
                api(projects.libCoreApiPublic)
                api(projects.libCoreEventsPublic)
                api(projects.libCryptoCore)

                api(projects.libDataLinkHttpClientImpl)

                // OAuth2 common models
                api(projects.libOauth2CommonPublic)

                // OAuth2 client implementation for introspection/metadata
                api(projects.libOauth2ClientImpl)

                // Trust lib for X.509 chain validation of HAIP wallet attestation x5c headers
                // (draft-ietf-oauth-attestation-based-client-auth + HAIP §4.4.1). lib-trust-x509
                // contributes an X509TrustValidationService into the Set<TrustValidationService>
                // multibinding which depends on TrustConfigProvider — bring lib-trust-core-impl
                // along so the graph resolves without the consumer having to wire it explicitly.
                api(projects.libTrustX509)
                api(projects.libTrustCoreImpl)

                // Dependency injection
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)

                // KV store (IAE session storage)
                api(projects.libDataStoreKvPublic)
                api(projects.libDataStoreKvImpl)
                api(projects.libDataStoreKvImplMemory)

                // Ktor for HTTP operations
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)

                // KMP-safe synchronization primitives (SynchronizedObject/synchronized) guarding
                // the in-process federation state in FederatedUserAuthenticationProvider and
                // the session-to-provider routing map in CompositeUserAuthenticationProvider.
                implementation(sphereonlib.org.jetbrains.kotlinx.atomicfu)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                // OAuth2 client implementation for introspection/metadata

                implementation(projects.libOauth2ClientImpl)
                implementation(projects.libCoreTest)
                implementation(projects.libCryptoKmsProviderSoftware)
                // Need default implementations for SessionExecution and other core dependencies
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCoreEventsImpl)
                // OidcTokenClaimExtractor default binding, needed by FederatedUserAuthenticationProvider
                implementation(projects.libOauth2CommonImpl)
            }
        }
    }
}

// Operator helper: hash a single password for ConfigBackedUserAuthenticationProvider. Reads the
// username, password, deployment salt, and iteration count via Gradle properties or environment
// variables, prints `oauth2.users.accounts.<username>.password=<base64-hash>` on stdout.
val hashPassword by tasks.registering(JavaExec::class) {
    group = "tools"
    description = "Hash a password for ConfigBackedUserAuthenticationProvider " +
        "(args: -Pusername -Ppassword -Pdeployment.salt -Pdeployment.iterations)."
    val jvmMainCompilation =
        kotlin.targets
            .getByName("jvm")
            .compilations
            .getByName("main")
    classpath = files(jvmMainCompilation.runtimeDependencyFiles, jvmMainCompilation.output.allOutputs)
    mainClass.set("com.sphereon.oauth2.server.authorization.impl.provider.HashPasswordCliKt")

    val usernameProvider =
        providers
            .gradleProperty("username")
            .orElse(providers.environmentVariable("HASH_USERNAME"))
    val passwordProvider =
        providers
            .gradleProperty("password")
            .orElse(providers.environmentVariable("HASH_PASSWORD"))
    val saltProvider =
        providers
            .gradleProperty("deployment.salt")
            .orElse(providers.environmentVariable("OAUTH2_USERS_PASSWORD_SALT"))
    val iterationsProvider =
        providers
            .gradleProperty("deployment.iterations")
            .orElse(providers.environmentVariable("OAUTH2_USERS_PASSWORD_ITERATIONS"))
            .orElse("210000")

    doFirst {
        args =
            listOf(
                usernameProvider.get(),
                passwordProvider.get(),
                saltProvider.get(),
                iterationsProvider.get(),
            )
    }
}
