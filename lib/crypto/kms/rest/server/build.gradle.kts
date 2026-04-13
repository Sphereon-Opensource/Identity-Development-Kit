import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(libs.plugins.metro)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)

    // Spring Boot plugins removed - all tests migrated to Ktor embedded server
    // alias(sphereonplug.plugins.org.springframework.boot) // FIXME uncomment when gradle-support branch is in Nexus
    // alias(sphereonplug.plugins.io.spring.dependency-management)  // FIXME uncomment when gradle-support branch is in Nexus
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

extra["kotlin.version"] = "2.3.20"
extra["kotlin-serialization.version"] = "1.9.0"

kotlin {
    kotlin.applyDefaultHierarchyTemplate()

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

                // Kotlin
                api(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                // DI (Metro)
                implementation(libs.amz.metro.public)
                implementation(libs.amz.metro.impl)
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
                implementation(projects.libDataLinkHttpClientImpl)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libCryptoKmsProviderRest)
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

configurations.all {
    exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
    exclude(group = "com.fasterxml.jackson.module", module = "jackson-module-kotlin")
    exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
}

tasks.named("jvmTest") {
    dependsOn("compileTestKotlinJvm")
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        force("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.9.0")
        // Force coroutines version for Ktor 3.3.3 compatibility
        // Must use eachDependency to override Spring Boot's dependency management
        eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-coroutines")) {
                useVersion("1.10.2")
            }
        }
    }
}

tasks.register<JavaExec>("runKtorServer") {
    group = "application"
    description = "Run the KMS REST server using Ktor Server with CIO engine"
    dependsOn("compileKtorServerKotlinJvm", "compileKotlinJvm")

    val mainCompilation = kotlin.jvm().compilations.getByName("main")

    classpath =
            mainCompilation.output.allOutputs
    mainClass.set("com.sphereon.crypto.kms.rest.server.KmsKtorServerKt")
}

// Helper task to publish to Maven Local without configuration cache
// Usage: ./gradlew publishLocalNoCache
tasks.register("publishLocalNoCache") {
    group = "publishing"
    description = "Publishes to Maven Local with configuration cache disabled (required due to Spring dependency management plugin)"
    doLast {
        println("Note: Run './gradlew publishToMavenLocal --no-configuration-cache' directly instead")
        println("This task is just a reminder that --no-configuration-cache is required for publishing")
    }
}
