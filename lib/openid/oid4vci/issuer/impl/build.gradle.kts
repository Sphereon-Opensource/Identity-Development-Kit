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
                api(projects.libOpenidOid4vciIssuerPublic)
                api(projects.libOpenidOid4vciCommonImpl)
                api(projects.libCoreEventsPublic)

                // SD-JWT issuance
                api(projects.libSdjwtPublic)
                api(projects.libSdjwtImpl)

                // JSON-LD context + schema validation for VCDM 2.0 enveloped issuance
                // (vc+ld+json+jwt). The public module supplies command contracts;
                // loader supplies the @ContributesBinding implementations + the
                // bundled W3C/UNTP @context registry.
                api(projects.libJsonldPublic)
                implementation(projects.libJsonldLoader)

                // Crypto (JWT/JWE, managed identifiers)
                api(projects.libCryptoCoreImpl)

                // DID provider registry (for signing key mode DID resolution)
                api(projects.libDidManagerPublic)
                // DID hosting SPI — contribute a provider that serves the issuer's own did:web
                // document (derived from its signing key) over the generic hosting endpoint.
                api(projects.libDidHostingPublic)

                // mDoc (ISO 18013-5 mobile documents)
                api(projects.libMdocCorePublic)
                api(projects.libMdocCoreImpl)

                // KV store
                api(projects.libDataStoreKvImpl)
                api(projects.libDataStoreKvImplMemory)

                // Credential design (for design-backed config provider)
                api(projects.libDataStoreCredentialDesignPublic)
                implementation(projects.libDataStoreCredentialDesignImpl)

                // OAuth2 AS (for SphereonAsBridge)
                api(projects.libOauth2ServerAuthorizationPublic)

                // X.509 trust anchors for key-attestation x5c chain validation
                // (OID4VCI 1.0 §7.2 — KeyAttestationVerifier)
                implementation(projects.libTrustX509)

                // Konform for validation
                api(sphereonlib.io.konform.konform)

                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCryptoCorePublic)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCoreEventsImpl)
                // Real in-memory status-list driver + enricher for the fail-closed issuance tests
                implementation(projects.libStatuslistImpl)
            }
        }
    }
}
