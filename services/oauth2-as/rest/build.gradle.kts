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
                // OAuth2 Authorization Server service logic
                api(projects.libOauth2ServerAuthorizationPublic)
                implementation(projects.libOauth2ServerAuthorizationImpl)

                // OAuth2 common models
                api(projects.libOauth2CommonPublic)
                implementation(projects.libOauth2CommonImpl)

                // OAuth2 resource-server validate command. UserInfo delegates access-token
                // binding validation (cnf.jkt, cnf.x5t#S256, expiry, scope) to
                // [com.sphereon.oauth2.server.resource.command.ValidateAccessTokenCommand]
                // so the binding contract lives in one place. The -impl module supplies the
                // session-scope command registry entry and JWT verifier; AS-rest code only
                // references types from -public.
                implementation(projects.libOauth2ServerResourcePublic)
                implementation(projects.libOauth2ServerResourceImpl)

                // OAuth2 client (for introspection/metadata)
                implementation(projects.libOauth2ClientImpl)

                // Software instance registry: the login page resolves the AS instance's
                // application identity (instance slug -> software party UUID) for theming.
                implementation(projects.libSoftwareRegistryPublic)

                // JWT validation: the -api module carries JwtValidationService/IdpRegistry/JwtValidationConfig
                // and the -impl module supplies the SessionScope JwtValidationService + AppScope IdpRegistry
                // bindings, so an AS assembly can validate bearers against a configured issuer's JWKS.
                implementation(projects.libOauth2JwtValidationApi)
                implementation(projects.libOauth2JwtValidationImpl)

                // Core
                api(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)
                api(projects.libCryptoCore)
                implementation(projects.libCryptoCorePublic)

                // HTTP client
                implementation(projects.libDataLinkHttpClientPublic)
                implementation(projects.libDataLinkHttpClientImpl)

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
                implementation(projects.ktorServerKotlinInject)

                // YAML config (must be direct dep for Metro to discover YamlFileAppPropertySourceImpl)
                implementation(projects.libConfYaml)
                implementation(sphereonlib.io.ktor.server.core)
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.server.content.negotiation)
                implementation(sphereonlib.io.ktor.server.status.pages)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)

                // Crypto providers for runtime
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)

                // Events
                implementation(projects.libCoreEventsImpl)
            }
        }
    }
}
