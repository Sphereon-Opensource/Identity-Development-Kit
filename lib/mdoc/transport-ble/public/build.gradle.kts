import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()
    jvm()

    androidLibrary {
        namespace = "com.sphereon.mdoc.transport.ble"
        compileSdk = 35
        minSdk = 27
    }

    configureIosTargetsIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Transport core abstractions
                api(projects.libMdocCorePublic)

                // BLE platform dependencies
                api(projects.libDataLinkBlePublic)

                // Coroutines
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
            }
        }
    }
}
