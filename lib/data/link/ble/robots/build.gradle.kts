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

import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}
metro {
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()

    jvm()

    configureIosTargetsIfEnabled()

    androidLibrary {
        namespace = "com.sphereon.data.link.ble.robot"
        compileSdk = 35
        minSdk = 27
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // BLE public API (interfaces)
                api(projects.libDataLinkBlePublic)

                // BLE test fixtures (fakes)
                api(projects.libDataLinkBleTestFixtures)

                // Core API
                api(projects.libCoreApiPublic)

                // Amazon App Platform robot support
                api(libs.bundles.app.platform.di)

                // Test assertions
                api(sphereonlib.io.kotest.assertions.core)
            }
        }
    }
}
