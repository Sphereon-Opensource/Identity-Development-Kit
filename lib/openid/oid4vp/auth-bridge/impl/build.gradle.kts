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
                // Metro needs to discover JsonLdContextValidator / JsonLdSchemaValidator
                // @Inject constructors at test-graph composition time. The validators
                // are reached transitively through oid4vp-verifier-impl but with
                // implementation-scope and so don't reach the test compile classpath.
                implementation(projects.libJsonldLoader)
                implementation(projects.libIdentityMatchingImpl)

                // OAuth2 client and common for verifier dependencies
                implementation(projects.libOauth2ClientImpl)
                implementation(projects.libOauth2CommonImpl)

                // DCQL store bindings transitively required by KvAuthorizationSessionStore
                implementation(projects.libOpenidOid4vpDcqlStoreImpl)

                // SD-JWT for verifier dependencies
                implementation(projects.libSdjwtImpl)

                // mdoc + trust bindings required by VerifyHolderBindingCommandImpl
                // (transitively pulled in via verifier-impl on the test classpath through
                // lib-openid-oid4vp-universal-impl).
                implementation(projects.libMdocCoreImpl)
                implementation(projects.libCborImpl)
                implementation(projects.libTrustX509)
                implementation(projects.libTrustCoreImpl)

                // Ktor mock engine for HTTP client testing
                implementation(sphereonlib.io.ktor.client.mock)
            }
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
