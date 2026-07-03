import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled
/*
 * Theme Compose SDK module (IDK layer).
 *
 * Provides Compose Multiplatform integration for the theme system:
 * - DefaultTheme: Composable that provides M3 MaterialTheme from ResolvedTheme
 * - ThemeTokenMapper: Maps ResolvedTheme tokens to M3 ColorScheme
 * - ClientPaletteResolver: Local M3 palette generation from a seed color
 * - LocalThemeTokens: CompositionLocal for raw token access
 *
 * No network dependencies (Ktor). For API-connected theming, use edk-theme-compose.
 * Depends only on lib-conf-theme-core-public. AGPL licensed.
 */

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

    androidLibrary {
        namespace = "com.sphereon.conf.theme.compose"
        compileSdk = 35
        minSdk = 27
    }
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                browser()
                nodejs()
            }
        }
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

                // Compose runtime — available on ALL targets including JS
                implementation(compose.runtime)

                // DateTime (for ClientPaletteResolver)
                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Intermediate source set for Compose UI platforms (JVM + iOS).
        // compose.foundation, compose.material3, and compose.ui do NOT support JS,
        // so files that depend on them live here instead of commonMain.
        val composeUiMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }
        val jvmMain by getting {
            dependsOn(composeUiMain)
        }
        findByName("wasmJsMain")?.dependsOn(composeUiMain)
        val jvmTest by getting {
            dependencies {
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }
        findByName("androidMain")?.dependsOn(composeUiMain)
        findByName("iosMain")?.dependsOn(composeUiMain)
    }
}
