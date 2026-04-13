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
 * SQLite implementation of the DID persistence layer.
 * This module provides SQLDelight-generated queries for SQLite and
 * implements the DidRepository interface defined in persistence-api.
 */

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("app.cash.sqldelight")
    id("maven-publish")
}
metro {
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Persistence API (dialect-agnostic interfaces)
                api(projects.libDidPersistenceApi)
                api(projects.libDidManagerPublic)
                api(projects.libCoreApiPublic)

                // SQLDelight
                implementation(sphereonlib.app.cash.sqldelight.runtime)
                implementation(sphereonlib.app.cash.sqldelight.coroutines.extensions)

                // Kotlin
                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                // DI (Metro)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(sphereonlib.app.cash.sqldelight.jdbc.driver)
                implementation(sphereonlib.org.xerial.sqlite.jdbc)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test.junit5)
            }
        }
    }
}

sqldelight {
    databases {
        create("DidDatabaseSqlite") {
            packageName.set("com.sphereon.did.persistence.sqlite")
            srcDirs("src/commonMain/sqldelight")
            verifyMigrations.set(false)
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.2.1")
        }
    }
}

// Skip verify migration tasks on Windows due to SQLite JDBC native library extraction issues
// SQLite JDBC tries to write to C:\windows which requires admin permissions
// Fix: Set JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=%TEMP% as a Windows System Environment Variable
if (System.getProperty("os.name").lowercase().contains("win")) {
    tasks.withType<app.cash.sqldelight.gradle.VerifyMigrationTask>().configureEach {
        enabled = false
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
