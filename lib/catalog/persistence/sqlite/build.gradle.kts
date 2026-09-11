import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    id("app.cash.sqldelight")
    id("maven-publish")
}

metro {
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libCatalogPersistenceApi)
                api(projects.libCatalogPublic)
                api(projects.libCoreApiPublic)
                implementation(sphereonlib.app.cash.sqldelight.runtime)
                implementation(sphereonlib.app.cash.sqldelight.coroutines.extensions)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
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
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}

sqldelight {
    databases {
        create("CatalogDatabaseSqlite") {
            packageName.set("com.sphereon.catalog.persistence.sqlite")
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
