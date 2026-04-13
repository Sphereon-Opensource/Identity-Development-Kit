import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}
metro {
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "com.sphereon.mdoc.transport.nfc"
        compileSdk = 35
        minSdk = 27
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        packaging {
            resources {
                excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            }
        }
        withHostTest { }
        withDeviceTest { }
    }

    jvm()

    configureIosTargetsIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Transport core abstractions
                api(projects.libCoreApiPublic)
                api(projects.libCborPublic)
                api(projects.libMdocCorePublic)
                api(projects.libMdocDatatransferPublic)

                // NFC platform dependencies
                api(projects.libDataLinkNfcPublic)

                // DI
                implementation(libs.bundles.app.platform.di)

                // Coroutines
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.atomicfu)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCoreApiDefault)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.io.kotest.assertions.core)
                implementation(sphereonlib.io.kotest.framework.engine)
                implementation(sphereonlib.io.kotest.property)
            }
        }

        findByName("androidMain")?.dependencies {
            implementation(sphereonlib.androidx.core.ktx)
        }

        val androidHostTest by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(projects.libCoreApiDefault)
                implementation(sphereonlib.io.mockk.mockk)
                implementation(sphereonlib.app.cash.turbine.turbine)
            }
        }
    }
}
