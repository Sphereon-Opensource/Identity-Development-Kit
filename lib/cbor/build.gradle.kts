/*
 * Facade module for backward compatibility.
 * Re-exports lib-cbor-public and lib-cbor-impl.
 * External consumers can continue to use lib-cbor without changes.
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
                api(projects.libCborPublic)
//                api(projects.libCborImpl)
            }
        }
    }
}
