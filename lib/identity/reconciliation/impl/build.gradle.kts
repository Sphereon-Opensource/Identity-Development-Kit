import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.metro)
}

metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val jsTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core.js)
            }
        }
        val commonMain by getting {
            dependencies {
                implementation(libs.skie.configuration.annotations)

                // Public API
                api(projects.libIdentityReconciliationPublic)

                // Identity Matching - for CreateIdentityMatchCommand
                api(projects.libIdentityMatchingPublic)

                // OAuth2 Client - for CreatePkceCommand, ExchangeTokenCommand
                api(projects.libOauth2ClientPublic)

                // OAuth2 JWT Validation API - for OidcDiscoveryService
                api(projects.libOauth2JwtValidationApi)

                // Core dependencies
                api(projects.libCoreApiPublic)
                api(libs.bundles.app.platform.di)
                api(libs.amz.metro.public)
                implementation(libs.amz.metro.impl)

                // Crypto (KMS for KmsBackedReconciliationCryptoService)
                implementation(projects.libCryptoCorePublic)

                // Identity Matching Impl - KmsBackedReconciliationCryptoService canonical home
                implementation(projects.libIdentityMatchingImpl)

                // KV store abstraction (backend-agnostic persistence)
                implementation(projects.libDataStoreKvPublic)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libIdentityMatchingImpl)
                implementation(projects.libOauth2ClientImpl)
                implementation(projects.libOauth2CommonImpl)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCryptoKmsProviderSoftware)
                // Kottage-backed KvStore for persistence tests
                implementation(projects.libDataStoreKvImplKottage)
                implementation(libs.kottage)
            }
        }
    }
}
