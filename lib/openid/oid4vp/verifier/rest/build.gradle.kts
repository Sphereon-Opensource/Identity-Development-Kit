plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}

metro {
}

// Reusable OID4VP REST adapters: the verifier public-endpoint adapter (request_uri,
// direct_post) and the Universal OID4VP adapter, plus their endpoint commands and descriptor
// providers. No standalone server, no ktor-server, no DCQL store binding: a service assembly
// mounts these adapters on its own transport and supplies its own store. The standalone
// deployable lives in `services-oid4vp-verifier-rest`, which depends on this module.
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
                // OID4VP Verifier + Universal service logic
                api(projects.libOpenidOid4vpVerifierPublic)
                api(projects.libOpenidOid4vpVerifierImpl)
                api(projects.libOpenidOid4vpUniversalPublic)
                api(projects.libOpenidOid4vpUniversalImpl)
                api(projects.libOpenidOid4vpCommonPublic)
                api(projects.libOpenidOid4vpDcql)

                // JsonLd validators are reached transitively through verifier-impl with
                // implementation scope; a consuming graph needs them on the compile classpath
                // so Metro can discover their @Inject ctors.
                implementation(projects.libJsonldLoader)

                // Core
                api(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCore)
                implementation(projects.libCryptoCorePublic)

                // KV storage (for session stores)
                implementation(projects.libDataStoreKvPublic)
                implementation(projects.libDataStoreKvImpl)
                implementation(projects.libDataStoreKvImplMemory)

                // SD-JWT
                implementation(projects.libSdjwtPublic)

                // HTTP client (for callbacks)
                implementation(projects.libDataLinkHttpClientPublic)

                // DI (Metro)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)

                // Serialization
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
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
