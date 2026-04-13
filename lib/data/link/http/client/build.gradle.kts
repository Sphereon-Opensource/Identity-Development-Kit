/*
 * Facade module for backward compatibility.
 * Re-exports lib-data-link-http-client-public and lib-data-link-http-client-impl.
 * External consumers can continue to use lib-data-link-http-client without changes.
 */

import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    id("maven-publish")
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libDataLinkHttpClientPublic)
                api(projects.libDataLinkHttpClientImpl)
            }
        }
    }
}
