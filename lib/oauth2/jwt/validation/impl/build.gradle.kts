/*
 * © 2025 Sphereon International B.V.
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
    alias(libs.plugins.metro)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // JWT validation API
                api(projects.libOauth2JwtValidationApi)

                // IDK core API
                api(projects.libCoreApiPublic)

                // IDK OAuth2 Resource Server (VerifyJwtCommand)
                implementation(projects.libOauth2ServerResourcePublic)
                implementation(projects.libOauth2ServerResourceImpl)

                // IDK OAuth2 Client (FetchAuthorizationServerMetadataCommand)
                implementation(projects.libOauth2ClientPublic)
                implementation(projects.libOauth2ClientImpl)

                // IDK Crypto Core (identifier resolution)
                implementation(projects.libCryptoCore)

                // DI (Metro)
                implementation(libs.bundles.app.platform.di)
                implementation(libs.amz.metro.public)

                // Kotlin serialization
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                api(sphereonlib.org.jetbrains.kotlinx.datetime)
                api(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }

    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}