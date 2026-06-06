import com.sphereon.gradle.plugin.configureStandardTargets

/*
 * REST surface for the free-form credential-definition authoring API.
 *
 * Thin `HttpEndpointCommand` impls wired into a single `CommandBackedHttpAdapter` mounted at
 * `/api/v1`. Each endpoint deserializes its args (path identifiers merged into the body by the
 * binary transport) and delegates to the matching free-form credential-definition service command.
 */
plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}

metro {
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libDataCredentialDefinitionPublic)
                api(projects.libCoreApiPublic)

                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                implementation(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
                api(sphereonlib.software.amazon.app.platform.metro.impl)
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
