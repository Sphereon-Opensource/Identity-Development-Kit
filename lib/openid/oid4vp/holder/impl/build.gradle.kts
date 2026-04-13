import com.sphereon.gradle.plugin.configureStandardTargets
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
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
                implementation(sphereonlib.co.touchlab.skie.configuration.annotations)
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCore)

                // OAuth2 common and client (for JAR support)
                api(projects.libOauth2CommonPublic)
                api(projects.libOauth2ClientPublic)

                // OID4VP public APIs
                api(projects.libOpenidOid4vpHolderPublic)
                api(projects.libOpenidOid4vpCommonPublic)
                api(projects.libOpenidOid4vpCommonImpl)
                api(projects.libOpenidOid4vpDcql)

                // Serialization
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)

                // Validation (konform)
                api(sphereonlib.io.konform.konform)

                // HTTP client for metadata resolution and request_uri fetching
                api(sphereonlib.io.ktor.client.core)
                api(sphereonlib.io.ktor.client.content.negotiation)
                api(sphereonlib.io.ktor.serialization.kotlinx.json)
                api(projects.libDataLinkHttpClientPublic)

                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCoreTest)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(sphereonlib.io.ktor.client.mock)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test.junit5)
                implementation(sphereonlib.io.ktor.server.core)
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)
            }
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
