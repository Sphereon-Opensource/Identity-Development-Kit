/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-compat-annotations:$version" else project(":lib-core-compat-annotations"))
                api(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-did-core-public:$version" else project(":lib-did-core-public"))
                api(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-did-manager-public:$version" else project(":lib-did-manager-public"))
                api(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-public:$version" else project(":lib-core-api-public"))
                api(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.core)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                api(sphereonlib.app.cash.sqldelight.runtime)
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
