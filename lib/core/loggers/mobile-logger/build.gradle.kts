import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    id("maven-publish")
}
metro {
}

kotlin {
    jvm()

    androidLibrary {
        namespace = "com.sphereon.core.log.mobile"
        compileSdk = 36
        minSdk = 27
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
                excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            }
        }
    }

    configureIosTargetsIfEnabled()
//    configureLinuxTargetIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                api(projects.libCoreApiPublic)
                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                // default deps are already injected by conventions plugin!
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCoreApiDefault)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
        findByName("androidMain")?.dependencies {
            implementation(sphereonlib.androidx.core.ktx)
            // Remove lifecycle dependency as it's causing issues
            // implementation(sphereonlib.androidx.lifecycle.runtime.ktx)
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        // iosMain: no platform-specific config needed (created by hierarchy template when iOS targets enabled)
    }
}
