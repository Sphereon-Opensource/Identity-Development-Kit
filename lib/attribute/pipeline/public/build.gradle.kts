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
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libCoreApiPublic)
                api(projects.libAttributeFlowPublic)
                api(projects.libAttributeMappingPublic)
                api(projects.libDataIntegrationPublic)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}
