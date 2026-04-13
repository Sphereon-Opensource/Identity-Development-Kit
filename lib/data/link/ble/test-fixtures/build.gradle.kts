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
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}
metro {
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()

    jvm()
    // Note: JS platform excluded - BLE is not supported on JS
    configureIosTargetsIfEnabled()

    androidLibrary {
        namespace = "com.sphereon.data.link.ble.test"
        compileSdk = 35
        minSdk = 27
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // BLE public API (interfaces we're mocking)
                api(projects.libDataLinkBlePublic)

                // Core utilities
                api(projects.libCoreApiPublic)
                api(projects.libCborPublic)

                // Kotlin coroutines for async testing
                api(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                // Atomicfu runtime (compiler plugin handles transformations in Kotlin 2.3+)
                implementation(sphereonlib.org.jetbrains.kotlinx.atomicfu)

                // DateTime for session timeouts
                api(sphereonlib.org.jetbrains.kotlinx.datetime)

                // DI for test component integration
                api(libs.bundles.app.platform.di)
            }
        }
    }
}
