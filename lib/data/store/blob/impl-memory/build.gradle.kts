import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    id("maven-publish")
}
metro {
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libDataStoreBlobPublic)
                implementation(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
                api(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCoreApiDefault)
            }
        }
    }
}
