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
    kotlin.applyDefaultHierarchyTemplate()

    // Allow internal API usage from the xmlutil library
    sourceSets.all {
        languageSettings {
            optIn("nl.adaptivity.xmlutil.XmlUtilInternal")
        }
    }

    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                api(projects.libCatalogEuPublic)
                api(projects.libTrustEtsi)
                api(projects.libTrustEtsiEntitiesPublic)
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCorePublic)
                implementation(projects.libCryptoCoreImpl)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.io.github.pdvrieze.xmlutil.core)
                implementation(sphereonlib.io.github.pdvrieze.xmlutil.serialization)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libTrustCoreImpl)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libTrustX509)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(sphereonlib.org.bouncycastle.bcprov.jdk18on)
                implementation(sphereonlib.org.bouncycastle.bcpkix.jdk18on)
                implementation(sphereonlib.io.ktor.client.cio.jvm)
            }
        }
    }
}
