/**
 * Role-neutral OAuth2 authorization-server HTTP capability.
 *
 * This module contains the adapters, endpoint commands, response mapping, and
 * descriptor contributions shared by executable AS assemblies. It deliberately
 * has no server bootstrap or executable graph ownership.
 */

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}

metro {
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // OAuth2 authorization-server contracts. Neutral HTTP endpoints still compile
                // against authorization-impl helpers (AcceptLanguageNegotiation, LoginCsrfTokenizer);
                // executable AS assemblies own final Metro graph selection over those bindings.
                api(projects.libOauth2ServerAuthorizationPublic)
                implementation(projects.libOauth2ServerAuthorizationImpl)

                // OAuth2 common models and client/resource capabilities used by
                // metadata, introspection, and user-info handlers.
                api(projects.libOauth2CommonPublic)
                implementation(projects.libOauth2CommonImpl)
                implementation(projects.libOauth2ServerResourcePublic)
                implementation(projects.libOauth2ServerResourceImpl)
                implementation(projects.libOauth2ClientImpl)

                // Core, crypto, registry, and JWT validation APIs used by the
                // neutral HTTP surface. Runtime bindings remain in assemblies.
                api(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)
                api(projects.libCryptoCore)
                implementation(projects.libCryptoCorePublic)
                implementation(projects.libSoftwareRegistryPublic)
                implementation(projects.libOauth2JwtValidationApi)
                implementation(projects.libOauth2JwtValidationImpl)

                implementation(projects.libDataLinkHttpClientPublic)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
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
