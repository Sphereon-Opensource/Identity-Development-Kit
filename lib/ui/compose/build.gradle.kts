/**
 * UI Component Library (IDK layer).
 *
 * Provides token-styled Compose Multiplatform components that consume
 * Tier 3 component tokens from the theme system. Components wrap M3
 * Foundation/Material3 primitives with token-resolved defaults.
 *
 * Depends on lib-conf-theme-compose for CompositionLocals and token access.
 * AGPL licensed.
 */

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.compose)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.compose)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()
    jvm()

    androidLibrary {
        namespace = "com.sphereon.conf.theme.ui.compose"
        compileSdk = 35
        minSdk = 27
    }

    js(IR) {
        browser()
        nodejs()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libConfThemeCorePublic)
                api(projects.libConfThemeCompose)

                // Compose runtime — available on ALL targets including JS
                implementation(compose.runtime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Intermediate source set for Compose UI platforms (JVM + iOS).
        // compose.foundation, compose.material3, and compose.ui do NOT support JS,
        // so component implementations live here.
        val composeUiMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.uiToolingPreview)

                // BlobService types for BlobServiceDataSource adapter
                api(projects.libDataStoreBlobPublic)
            }
        }
        val jvmMain by getting {
            dependsOn(composeUiMain)
        }
        val jvmTest by getting {
            dependencies {
                implementation(compose.foundation)
                implementation(compose.material3)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(compose.desktop.currentOs)
            }
        }
        val androidMain by getting {
            dependsOn(composeUiMain)
        }
        val iosMain by getting {
            dependsOn(composeUiMain)
        }
    }
}
