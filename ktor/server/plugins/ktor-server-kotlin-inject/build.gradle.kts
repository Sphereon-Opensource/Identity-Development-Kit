import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.GenerateMavenPom

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.metro)
    alias(sphereonplug.plugins.io.ktor.plugin)
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()

    jvm()
    js {
        nodejs {
            useEsModules()
            binaries.library()
            generateTypeScriptDefinitions()
        }
    }
    /*iosX64()
    iosArm64()
    iosSimulatorArm64()*/

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Core IDK
                api(projects.libCoreApiPublic)

                // kotlin-inject dependencies
                api(libs.bundles.app.platform.di)

                // Ktor Server (multiplatform)
                api(sphereonlib.io.ktor.server.core)

                // Kotlin reflection (multiplatform)
                implementation(sphereonlib.org.jetbrains.kotlin.reflect)

                // Coroutines (multiplatform)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                // UUID support (multiplatform)
                implementation(sphereonlib.org.jetbrains.kotlin.stdlib)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libDataLinkHttpClientImpl)

                // Ktor testing (multiplatform core only)
                implementation(sphereonlib.io.ktor.server.test.host)
            }
        }

        val jvmMain by getting {
            dependencies {
                // JVM-specific ktor extensions (optional auth support)
                compileOnly(sphereonlib.io.ktor.server.auth)
                compileOnly(sphereonlib.io.ktor.server.auth.jwt)

                // YAML parsing for KtorYamlPropertySource
                implementation("org.yaml:snakeyaml:2.2")
            }
        }

        val jvmTest by getting {
            dependencies {
                // JVM-specific test dependencies
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.server.auth)
                implementation(sphereonlib.io.ktor.client.content.negotiation)

                // IDK test dependencies (JVM only as they may not have JS/Native targets)
                implementation(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCore)
                implementation(projects.libCryptoKmsProviderSoftware)
                // Required for HttpClientFactory (used by JwksUrlExternalIdentifierResolutionService)
                implementation(projects.libDataLinkHttpClientImpl)
            }
        }

        val jsMain by getting {
            dependencies {
                // JS-specific dependencies
                implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.20")
            }
        }

        val jsTest by getting {
            dependencies {
                // JS-specific test dependencies
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test.js)
                // Note: ktor-server-cio is not available for JS
                // JS tests use the test-host engine which is multiplatform
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.server.auth)
                implementation(sphereonlib.io.ktor.client.content.negotiation)

                // IDK test dependencies (JVM only as they may not have JS/Native targets)

                implementation(projects.libCoreApiPublic)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCore)
                implementation(projects.libCryptoKmsProviderSoftware)
            }
        }
    }
}

// Disable configuration cache for publishing tasks due to vanniktech plugin limitations
tasks.withType<GenerateMavenPom>().configureEach {
    notCompatibleWithConfigurationCache("POM generation is not compatible with configuration cache due to vanniktech maven publish plugin")
}
tasks.withType<PublishToMavenRepository>().configureEach {
    notCompatibleWithConfigurationCache("Publishing tasks are not compatible with configuration cache due to vanniktech maven publish plugin")
}
tasks.withType<PublishToMavenLocal>().configureEach {
    notCompatibleWithConfigurationCache("Publishing tasks are not compatible with configuration cache due to vanniktech maven publish plugin")
}
