/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
                api(projects.libCryptoCertificatePersistenceApi)
                api(projects.libCoreApiPublic)
                implementation(sphereonlib.app.cash.sqldelight.runtime)
                implementation(sphereonlib.app.cash.sqldelight.coroutines.extensions)
                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(sphereonlib.app.cash.sqldelight.jdbc.driver)
                implementation(sphereonlib.org.xerial.sqlite.jdbc)
                implementation(sphereonlib.com.zaxxer.hikaricp)
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
        create("CertificateReferenceDatabaseSqlite") {
            packageName.set("com.sphereon.crypto.certificate.persistence.sqlite")
            srcDirs("src/commonMain/sqldelight")
            verifyMigrations.set(false)
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.3.2")
        }
    }
}

if (System.getProperty("os.name").lowercase().contains("win")) {
    tasks.withType<app.cash.sqldelight.gradle.VerifyMigrationTask>().configureEach {
        enabled = false
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
