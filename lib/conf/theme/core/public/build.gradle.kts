/**
 * Theme core public module (IDK layer).
 *
 * Pure KMP models, interfaces, and utilities for theming:
 * - Theme models (definitions, tokens, variants, scopes)
 * - ThemeStore and ThemeResolver interfaces
 * - Material Design 3 palette generation (HCT color space)
 * - Token flattening and reference resolution
 * - Validation and system defaults
 *
 * No DI, no REST. Consumed by theme-core-impl (IDK) and theme modules (VDX).
 */

import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
}

kotlin {
    configureStandardTargets()

    js {
        outputModuleName = "@sphereon/kmp-theme-core-public"
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libCoreCompat)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }

    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}
