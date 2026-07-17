import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libDataStoreVaultPublic)
                api(projects.libCryptoCorePublic)
                implementation(projects.libCompression)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.core)
                implementation(sphereonlib.org.kotlincrypto.core.digest)
                implementation(sphereonlib.org.kotlincrypto.hash.sha2)
                implementation(sphereonlib.com.doist.x.normalize)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.dev.whyoleg.cryptography.core)
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.optimal)
            }
        }
    }
}
