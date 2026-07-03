import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    id("maven-publish")
}
metro {
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()

    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    androidLibrary {
        namespace = "com.sphereon.crypto.kms.provider.mobile"
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
    }

    configureIosTargetsIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.libCoreApiPublic)
                implementation(projects.libCborPublic)
                implementation(projects.libCryptoCore)
                // Impl because we are directly using MemoryKeystore instantiations
                implementation(projects.libCryptoCoreImpl)
                implementation(libs.bundles.app.platform.di)

                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.cbor)
                implementation(sphereonlib.org.jetbrains.kotlinx.io.core)
                implementation(sphereonlib.at.asitplus.signum.supreme)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCryptoSecdsaImpl)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCoreTest)
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCoreApiDefault)
            }
        }
    }
}
