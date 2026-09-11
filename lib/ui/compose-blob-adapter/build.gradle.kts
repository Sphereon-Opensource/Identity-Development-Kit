import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureJsTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvm()

    android {
        namespace = "com.sphereon.conf.theme.ui.compose.blob.adapter"
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
                api(projects.libUiCompose)
                api(projects.libDataStoreBlobPublic)
            }
        }
    }
}
