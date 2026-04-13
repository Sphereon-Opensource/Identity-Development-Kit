import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.metro)
}

metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val jsTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core.js)
            }
        }
        val commonMain by getting {
            dependencies {
                api(projects.libCoreApiPublic)
                api(projects.libOpenidOid4vpUniversalPublic)
                api(projects.libIdentityMatchingPublic)
                api(projects.libIdentityReconciliationPublic)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                api(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                api(libs.bundles.app.platform.di)
                api(libs.amz.metro.public)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}

