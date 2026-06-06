import com.sphereon.gradle.plugin.configureStandardTargets

/*
 * IDK free-form credential-definition impl module.
 *
 * Hosts the in-memory `CredentialDefinitionStore` default and the ungated CRUD + claim-mutation +
 * version/lifecycle `ServiceCommand` implementations for the role-neutral free-form definition tier.
 * Each command resolves the tenant from the session execution context and persists through the
 * `CredentialDefinitionStore` SPI. No license gate: the free-form tier is open-core.
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

                api(sphereonlib.org.jetbrains.kotlinx.datetime)
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
