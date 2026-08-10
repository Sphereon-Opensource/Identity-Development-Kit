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
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)

                // OAuth2 common models
                implementation(projects.libOauth2CommonPublic)
                implementation(projects.libOauth2CommonImpl)

                // JWT validation registry used by the OAuth2 AS REST internal surfaces.
                implementation(projects.libOauth2JwtValidationApi)
                implementation(projects.libOauth2JwtValidationImpl)

                // OAuth2 AS (OP)
                implementation(projects.libOauth2ServerAuthorizationPublic)
                implementation(projects.libOauth2ServerAuthorizationImpl)
                // The Ktor adapter binds the OAuth2 AS HttpAdapter set into the session graph.
                implementation(projects.servicesOauth2AsRest)

                // OAuth2 Resource Server (RS) — VerifyJwtCommand, ValidateAccessTokenCommand
                implementation(projects.libOauth2ServerResourcePublic)
                implementation(projects.libOauth2ServerResourceImpl)

                // OAuth2 Client (RP) — OAuth2Client, CompleteOidcLoginCommand
                implementation(projects.libOauth2ClientPublic)
                implementation(projects.libOauth2ClientImpl)

                // Core API
                implementation(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)

                // Events (AppEventService / EventHub / etc. — contributed bindings needed by
                // the session graph so token commands can emit audit events).
                implementation(projects.libCoreEventsImpl)

                // Crypto core + Software KMS (for OP signing keys)
                implementation(projects.libCryptoCorePublic)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoCore)
                implementation(projects.libCryptoKmsProviderSoftware)

                // HTTP client plumbing for InProcessHttpClientFactory
                implementation(projects.libDataLinkHttpClientPublic)
                implementation(projects.libDataLinkHttpClientImpl)

                // Ktor client + MockEngine for in-process routing
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.mock)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)

                // DI
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
    }
}
