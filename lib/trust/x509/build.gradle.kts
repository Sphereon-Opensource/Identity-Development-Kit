import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    id("maven-publish")
}
metro {
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                api(projects.libTrustCorePublic)
                implementation(projects.libTrustCoreImpl)
                api(projects.libCoreApiPublic)
                implementation(projects.libCryptoCorePublic)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.io.ktor.client.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libTrustCoreImpl)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCoreImpl)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
    }
}
