import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.metro)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libDataStoreBlobPublic)
                api(projects.libDataStoreOkdOpenapi)
                api(projects.libCoreApiPublic)
                api(projects.libDataLinkHttpClientPublic)

                api(sphereonlib.io.ktor.client.core)
                api(sphereonlib.io.ktor.client.content.negotiation)
                api(sphereonlib.io.ktor.client.auth)
                api(sphereonlib.io.ktor.serialization.kotlinx.json)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)

                implementation(libs.bundles.app.platform.di)
                api(libs.amz.metro.public)
                api(libs.amz.metro.impl)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(libs.amz.metro.impl)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(sphereonlib.io.ktor.client.cio)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCoreApiDefault)
            }
        }
    }
}

