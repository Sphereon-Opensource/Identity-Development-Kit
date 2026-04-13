import com.sphereon.gradle.plugin.configureStandardTargets

/*
 * Theme Web SDK module (IDK layer).
 *
 * Web-specific utilities for the theme system:
 * - CssTokenMapper: Converts IDK token keys to CSS custom properties
 * - CssLegacyAliases: Legacy alias maps for portal backwards compatibility
 * - WebBrandingTokens: Extracts structured branding from flat token maps
 * - FoucPreventionScript: Generates blocking inline script for FOUC prevention
 * - WebSystemDefaults: Pre-resolved flat token maps for light/dark
 *
 * No DOM dependencies in commonMain. DOM-specific code in jsMain.
 * Depends only on lib-conf-theme-core-public. AGPL licensed.
 */

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libConfThemeCorePublic)
                api(projects.libCoreApiPublic)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
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
