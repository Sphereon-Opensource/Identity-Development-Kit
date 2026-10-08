plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}
metro {
}

// Root integration modules consume owning pack publications. Explicit IDK_LOCAL_PACKS
// composites substitute these same coordinates for source development.
val idkArtifactVersion = rootProject.extra["platformVersion"].toString()

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
                implementation("com.sphereon.idk:lib-oauth2-common-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-oauth2-common-impl:$idkArtifactVersion")

                // JWT validation registry used by the OAuth2 AS REST internal surfaces.
                implementation("com.sphereon.idk:lib-oauth2-jwt-validation-api:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-oauth2-jwt-validation-impl:$idkArtifactVersion")

                // OAuth2 AS (OP)
                implementation("com.sphereon.idk:lib-oauth2-server-authorization-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-oauth2-server-authorization-impl:$idkArtifactVersion")
                // The Ktor adapter binds the OAuth2 AS HttpAdapter set into the session graph.
                implementation("com.sphereon.idk:services-oauth2-as-rest:$idkArtifactVersion")

                // OAuth2 Resource Server (RS) — VerifyJwtCommand, ValidateAccessTokenCommand
                implementation("com.sphereon.idk:lib-oauth2-server-resource-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-oauth2-server-resource-impl:$idkArtifactVersion")

                // OAuth2 Client (RP) — OAuth2Client, CompleteOidcLoginCommand
                implementation("com.sphereon.idk:lib-oauth2-client-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-oauth2-client-impl:$idkArtifactVersion")

                // Core API
                implementation("com.sphereon.idk:lib-core-api-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-core-api-default:$idkArtifactVersion")

                // Events (AppEventService / EventHub / etc. — contributed bindings needed by
                // the session graph so token commands can emit audit events).
                implementation("com.sphereon.idk:lib-core-events-impl:$idkArtifactVersion")

                // Crypto core + Software KMS (for OP signing keys)
                implementation("com.sphereon.idk:lib-crypto-core-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-crypto-core-impl:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-crypto-core:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-crypto-kms-provider-software:$idkArtifactVersion")

                // HTTP client plumbing for InProcessHttpClientFactory
                implementation("com.sphereon.idk:lib-data-link-http-client-public:$idkArtifactVersion")
                implementation("com.sphereon.idk:lib-data-link-http-client-impl:$idkArtifactVersion")

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
