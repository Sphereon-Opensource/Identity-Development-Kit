/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    // Metro plugin needed so `dev.zacsweers.metro` runtime annotations
    // (`@ContributesTo`, `@OptionalBinding`) used by the optional-binding accessor
    // interfaces in this module resolve at compile time. The metro-public dep brings
    // the API surface; the plugin adds the metro runtime jar to the compile classpath.
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}
metro {
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libCoreApiPublic)
                api(projects.libAttributePipelinePublic)
                api(projects.libCredentialClaimsMapperPublic)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
            }
        }
    }
}
