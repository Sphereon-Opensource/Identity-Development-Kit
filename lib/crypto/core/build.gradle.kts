/*
 * Facade module for backward compatibility.
 * Re-exports lib-crypto-core-public and lib-crypto-core-impl.
 * External consumers can continue to use lib-crypto-core without changes.
 */

import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    id("maven-publish")
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libCryptoCorePublic)
                api(projects.libCryptoCoreImpl)
            }
        }
    }
}
