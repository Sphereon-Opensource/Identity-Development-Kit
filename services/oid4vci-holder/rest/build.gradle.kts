plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
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
        val commonMain by getting {
            dependencies {
                // OID4VCI Holder service logic
                implementation(projects.libOpenidOid4vciHolderPublic)
                implementation(projects.libOpenidOid4vciHolderImpl)
                implementation(projects.libOpenidOid4vciCommonPublic)

                // Core
                implementation(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCorePublic)

                // KV storage (for session stores)
                implementation(projects.libDataStoreKvPublic)
                implementation(projects.libDataStoreKvImpl)
                implementation(projects.libDataStoreKvImplMemory)

                // HTTP client
                implementation(projects.libDataLinkHttpClientPublic)

                // DI
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)

                // Serialization & concurrency
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                // Ktor client
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
            }
        }
        val jvmMain by getting {
            dependencies {
                // Ktor server
                implementation(projects.ktorServerKotlinInject)
                implementation(sphereonlib.io.ktor.server.core)
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.server.content.negotiation)
                implementation(sphereonlib.io.ktor.server.status.pages)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)

                // Crypto & storage implementations
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libDataLinkHttpClientImpl)
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
