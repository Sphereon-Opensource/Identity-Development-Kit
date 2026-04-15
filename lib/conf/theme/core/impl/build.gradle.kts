plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    id("maven-publish")
}
/*
 * Theme core implementation module (IDK layer).
 *
 * DI-bound implementations of core-public interfaces:
 * - InMemoryThemeStore: Map-backed theme store
 * - DefaultThemeResolver: Basic resolution using ThemeStore + TokenFlattener
 */
metro {
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libConfThemeCorePublic)
                api(projects.libCoreApiPublic)

                // Kotlin
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                // DI (Metro)
                implementation(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(sphereonlib.software.amazon.app.platform.di.common.public)
                implementation(sphereonlib.software.amazon.app.platform.scope.public)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test.junit5)
            }
        }
    }

    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
