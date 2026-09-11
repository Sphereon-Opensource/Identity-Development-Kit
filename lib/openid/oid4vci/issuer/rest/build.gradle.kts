plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}

metro {
}

// Reusable OID4VCI HTTP adapters, endpoint commands, and descriptor providers.
// Default standalone adapter/handler map ownership deliberately lives in
// services-oid4vci-issuer-rest so enterprise tenant assemblies can contribute
// their tenant-aware implementations without replacement bindings.
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
                implementation(projects.libOpenidOid4vciRestPublic)
                implementation(projects.libOpenidOid4vciRestImpl)
                implementation(projects.libOpenidOid4vciIssuerPublic)
                implementation(projects.libOpenidOid4vciIssuerImpl)
                implementation(projects.libOauth2ServerResourcePublic)
                implementation(projects.libStatuslistPublic)
                implementation(projects.libStatuslistImpl)
                implementation(projects.servicesStatuslistRest)
                implementation(projects.libOpenidOid4vciCommonPublic)
                implementation(projects.libDataStoreCredentialDesignPublic)
                implementation(projects.libOpenidOid4vcCommonPublic)
                implementation(projects.libOpenidOid4vcCommonImpl)
                implementation(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCorePublic)
                implementation(projects.libDataStoreKvPublic)
                implementation(projects.libDataStoreKvImpl)
                implementation(projects.libDataStoreKvImplMemory)
                implementation(projects.libDataLinkHttpClientPublic)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.io.ktor.client.mock)
                implementation(projects.libOauth2ServerAuthorizationImpl)
            }
        }
    }
}
