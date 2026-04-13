import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()
    jvm()

    configureIosTargetsIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Re-export both public and impl modules
                api(projects.libMdocTransportBlePublic)
                api(projects.libMdocTransportBleImpl)
            }
        }

        val commonTest by getting {
            dependencies {
            }
        }
    }
}
