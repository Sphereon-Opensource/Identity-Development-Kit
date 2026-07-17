import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureJsTargetIfEnabled
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

    android {
        namespace = "com.sphereon.conf.theme.compose"
        compileSdk = 35
        minSdk = 27
    }
    configureJsTargetIfEnabled {
        browser()
        nodejs()
    }
    configureWasmJsTargetIfEnabled {
        browser()
    }
    configureIosTargetsIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libConfThemeCorePublic)

                // Compose runtime — available on ALL targets including JS
                implementation(sphereonlib.org.jetbrains.compose.runtime.runtime)

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

        // Shared Compose Multiplatform source set for JVM, Android, iOS, classic JS, and WasmJS.
        val composeUiMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(sphereonlib.org.jetbrains.compose.foundation.foundation)
                implementation(sphereonlib.org.jetbrains.compose.material3.material3)
            }
        }
        val jvmMain by getting {
            dependsOn(composeUiMain)
        }
        findByName("jsMain")?.dependsOn(composeUiMain)
        findByName("wasmJsMain")?.dependsOn(composeUiMain)
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.compose.foundation.foundation)
                implementation(sphereonlib.org.jetbrains.compose.material3.material3)
            }
        }
        findByName("androidMain")?.dependsOn(composeUiMain)
        findByName("iosMain")?.dependsOn(composeUiMain)
    }
}
