import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}

metro {
}

kotlin {
    configureStandardTargets()

    sourceSets {
        findByName("jsTest")?.dependencies {
            implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core.js)
        }
        val commonMain by getting {
            dependencies {
                api(projects.libOpenidOid4vpAuthBridgePublic)
                api(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)

                // Universal OID4VP for verifier integration
                api(projects.libOpenidOid4vpUniversalPublic)
                implementation(projects.libOpenidOid4vpUniversalImpl)

                // Claims Mapper (from Phase 1)
                api(projects.libCredentialClaimsMapperPublic)
                implementation(projects.libCredentialClaimsMapperImpl)

                // Identity Resolution for IdentityResolutionUserServiceImpl
                api(projects.libIdentityResolutionPublic)

                // Identity Reconciliation for IDV orchestration
                api(projects.libIdentityReconciliationPublic)
                implementation(projects.libIdentityReconciliationImpl)

                // Identity Matching for IdentifierType
                api(projects.libIdentityMatchingPublic)

                // OAuth2 Authorization Server for UserAuthenticationProvider
                api(projects.libOauth2ServerAuthorizationPublic)

                // Crypto for JwtService
                api(projects.libCryptoCorePublic)
                implementation(projects.libCryptoCoreImpl)

                // DID methods for holder key extraction (did:key)
                implementation(projects.libDidMethodsKey)

                // KV Store for session storage
                api(projects.libDataStoreKvPublic)

                // DI (Metro)
                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libDidManagerImpl)
                implementation(projects.libDidResolverImpl)
                implementation(projects.libDidMethodsKey)
                implementation(projects.libDidMethodsJwk)
                implementation(projects.libDidPersistenceMemory)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test.junit5)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCoreTest)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libDidManagerImpl)
                implementation(projects.libDidResolverImpl)
                implementation(projects.libDidMethodsKey)
                implementation(projects.libDidMethodsJwk)
                implementation(projects.libDidPersistenceMemory)
                implementation(projects.libIdentityMatchingImpl)

                // OAuth2 client and common for verifier dependencies
                implementation(projects.libOauth2ClientImpl)
                implementation(projects.libOauth2CommonImpl)

                // SD-JWT for verifier dependencies
                implementation(projects.libSdjwtImpl)

                // Ktor mock engine for HTTP client testing
                implementation(sphereonlib.io.ktor.client.mock)
            }
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
