plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    // Maven coordinates for consumers (OIDFed PLATFORM e2e, hosts). service-deployable only
    // builds fatJar/runServer — it does not register publishToMavenLocal / nexus publications.
    // Same pattern as services-kms-rest, services-statuslist-rest, services-did-*-rest.
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("com.sphereon.gradle.plugin.service-deployable")
}
metro {
}
serviceDeployable {
    mainClass.set("com.sphereon.oauth2.server.authorization.ktor.OAuth2AsKtorServerKt")
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
        val commonMain by getting {
            dependencies {
                api(if (rootProject.findProperty("idk.consumeProtocolsAsArtifacts") == "true") "com.sphereon.idk:lib-oauth2-server-rest:$version" else project(":lib-oauth2-server-rest"))

                // OAuth2 Authorization Server service logic
                api(if (rootProject.findProperty("idk.consumeProtocolsAsArtifacts") == "true") "com.sphereon.idk:lib-oauth2-server-authorization-public:$version" else project(":lib-oauth2-server-authorization-public"))
                implementation(if (rootProject.findProperty("idk.consumeProtocolsAsArtifacts") == "true") "com.sphereon.idk:lib-oauth2-server-authorization-impl:$version" else project(":lib-oauth2-server-authorization-impl"))

                // OAuth2 common models
                api(if (rootProject.findProperty("idk.consumeProtocolsAsArtifacts") == "true") "com.sphereon.idk:lib-oauth2-common-public:$version" else project(":lib-oauth2-common-public"))
                implementation(if (rootProject.findProperty("idk.consumeProtocolsAsArtifacts") == "true") "com.sphereon.idk:lib-oauth2-common-impl:$version" else project(":lib-oauth2-common-impl"))

                // OAuth2 resource-server validate command. UserInfo delegates access-token
                // binding validation (cnf.jkt, cnf.x5t#S256, expiry, scope) to
                // [com.sphereon.oauth2.server.resource.command.ValidateAccessTokenCommand]
                // so the binding contract lives in one place. The -impl module supplies the
                // session-scope command registry entry and JWT verifier; AS-rest code only
                // references types from -public.
                implementation(if (rootProject.findProperty("idk.consumeProtocolsAsArtifacts") == "true") "com.sphereon.idk:lib-oauth2-server-resource-public:$version" else project(":lib-oauth2-server-resource-public"))
                implementation(if (rootProject.findProperty("idk.consumeProtocolsAsArtifacts") == "true") "com.sphereon.idk:lib-oauth2-server-resource-impl:$version" else project(":lib-oauth2-server-resource-impl"))

                // OAuth2 client (for introspection/metadata)
                implementation(if (rootProject.findProperty("idk.consumeProtocolsAsArtifacts") == "true") "com.sphereon.idk:lib-oauth2-client-impl:$version" else project(":lib-oauth2-client-impl"))

                // Software instance registry: the login page resolves the AS instance's
                // application identity (instance slug -> software party UUID) for theming.
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-software-registry-public:$version" else project(":lib-software-registry-public"))

                // JWT validation: the -api module carries JwtValidationService/IdpRegistry/JwtValidationConfig
                // and the -impl module supplies the SessionScope JwtValidationService + AppScope IdpRegistry
                // bindings, so an AS assembly can validate bearers against a configured issuer's JWKS.
                implementation(if (rootProject.findProperty("idk.consumeProtocolsAsArtifacts") == "true") "com.sphereon.idk:lib-oauth2-jwt-validation-api:$version" else project(":lib-oauth2-jwt-validation-api"))
                implementation(if (rootProject.findProperty("idk.consumeProtocolsAsArtifacts") == "true") "com.sphereon.idk:lib-oauth2-jwt-validation-impl:$version" else project(":lib-oauth2-jwt-validation-impl"))

                // Core
                api(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-public:$version" else project(":lib-core-api-public"))
                implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-default:$version" else project(":lib-core-api-default"))
                api(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core:$version" else project(":lib-crypto-core"))
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core-public:$version" else project(":lib-crypto-core-public"))

                // HTTP client
                implementation(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-http-client-public:$version" else project(":lib-data-link-http-client-public"))
                implementation(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-http-client-impl:$version" else project(":lib-data-link-http-client-impl"))

                // DI (Metro)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)

                // Serialization + HTTP
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)

                // Ktor client
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

        val jvmMain by getting {
            dependencies {
                // Ktor server
                implementation(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:ktor-server-kotlin-inject:$version" else project(":ktor-server-kotlin-inject"))

                // YAML config (must be direct dep for Metro to discover YamlFileAppPropertySourceImpl)
                implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-conf-yaml:$version" else project(":lib-conf-yaml"))
                implementation(sphereonlib.io.ktor.server.core)
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.server.content.negotiation)
                implementation(sphereonlib.io.ktor.server.status.pages)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)

                // Crypto providers for runtime
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core-impl:$version" else project(":lib-crypto-core-impl"))
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-kms-provider-software:$version" else project(":lib-crypto-kms-provider-software"))

                // Events
                implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-events-impl:$version" else project(":lib-core-events-impl"))
            }
        }
    }
}
