/*
 * UI Component Library (IDK layer).
 *
 * Provides token-styled Compose Multiplatform components that consume
 * Tier 3 component tokens from the theme system. Components wrap M3
 * Foundation/Material3 primitives with token-resolved defaults.
 *
 * Depends on lib-conf-theme-compose for CompositionLocals and token access.
 * AGPL licensed.
 */

import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureJsTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled
plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.compose)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.compose)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()
    jvm()

    android {
        namespace = "com.sphereon.conf.theme.ui.compose"
        compileSdk = 35
    }
    configureJsTargetIfEnabled {
        browser()
        nodejs()
    }
    configureWasmJsTargetIfEnabled {
        browser()
        nodejs()
    }
    configureIosTargetsIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libConfThemeCorePublic)
                api(projects.libConfThemeCompose)

                // Compose runtime — available on ALL targets including JS
                implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Shared Compose Multiplatform source set for JVM, Android, iOS, classic JS, and WasmJS.
        val composeUiMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
                implementation("org.jetbrains.compose.material3:material3:1.11.0-alpha07")
                implementation("org.jetbrains.compose.ui:ui:1.11.1")
                implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.11.1")

            }
        }
        val jvmMain by getting {
            dependsOn(composeUiMain)
        }
        findByName("jsMain")?.dependsOn(composeUiMain)
        findByName("wasmJsMain")?.dependsOn(composeUiMain)
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
                implementation("org.jetbrains.compose.material3:material3:1.11.0-alpha07")
                implementation("org.jetbrains.compose.ui:ui-test:1.11.1")
                implementation(composeDesktopRuntime())
            }
        }
        findByName("androidMain")?.dependsOn(composeUiMain)
        findByName("iosMain")?.dependsOn(composeUiMain)
    }
}

fun composeDesktopRuntime() =
    when {
        System.getProperty("os.name").startsWith("Windows") -> "org.jetbrains.compose.desktop:desktop-jvm-windows-x64:1.11.1"
        System.getProperty("os.name").startsWith("Mac") && System.getProperty("os.arch") == "aarch64" ->
            "org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:1.11.1"
        System.getProperty("os.name").startsWith("Mac") -> "org.jetbrains.compose.desktop:desktop-jvm-macos-x64:1.11.1"
        System.getProperty("os.arch") == "aarch64" -> "org.jetbrains.compose.desktop:desktop-jvm-linux-arm64:1.11.1"
        else -> "org.jetbrains.compose.desktop:desktop-jvm-linux-x64:1.11.1"
    }
