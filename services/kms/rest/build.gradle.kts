plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
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
                // IDK core API (PropertySource, ConfigLevel, etc.)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCore)
                // Keep direct access to DI bindings like KeyStoreManagerImpl stable for KSP graph resolution.
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoCorePublic)
                implementation(projects.libCryptoKmsRestApi)
                implementation(projects.libCryptoCertificatePersistenceApi)

                // Kotlin
                api(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                // DI (Metro)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(sphereonlib.software.amazon.app.platform.di.common.public)
                implementation(sphereonlib.software.amazon.app.platform.scope.public)

                implementation(sphereonlib.org.jetbrains.kotlin.reflect)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.reactor)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCryptoCoreImpl)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }

        // JVM library code
        val jvmMain by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                implementation(projects.ktorServerKotlinInject)
                implementation(sphereonlib.io.ktor.server.core)
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.server.status.pages)
                implementation(sphereonlib.io.ktor.server.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libCryptoKmsProviderAws)
                implementation(projects.libCryptoKmsProviderAzure)
                implementation(projects.libCryptoKmsProviderRest)
                implementation(projects.libCryptoKeyPersistenceApi)
                implementation(projects.libCryptoKeyPersistenceImpl)
                implementation(projects.libCryptoCertificatePersistenceSqlite)
                implementation(projects.libDataLinkHttpClientImpl)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(sphereonlib.org.bouncycastle.bcprov.jdk18on)
                implementation(sphereonlib.org.bouncycastle.bcpkix.jdk18on)
                // Ktor testing
                implementation(projects.ktorServerKotlinInject)
                implementation(sphereonlib.io.ktor.server.core)
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.server.test.host)
                implementation(sphereonlib.io.ktor.server.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
                // Force coroutines version compatible with Ktor 3.3.3
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                // Ktor HttpClient for raw HTTP tests (controller/adapter integration tests)
                implementation(sphereonlib.io.ktor.client.cio)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
            }
        }
    }
}

tasks.named("jvmTest") {
    dependsOn("compileTestKotlinJvm")
}
