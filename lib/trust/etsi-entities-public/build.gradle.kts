import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    id("maven-publish")
}

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libTrustCorePublic)
                api(projects.libCoreApiPublic)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.io.github.pdvrieze.xmlutil.core)
                implementation(sphereonlib.io.github.pdvrieze.xmlutil.serialization)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
            }
        }
        findByName("jsTest")?.dependencies {
            implementation(sphereonlib.io.kotest.assertions.core)
            implementation(sphereonlib.io.kotest.framework.engine)
            implementation(sphereonlib.io.kotest.property)
        }
        // appleMain: no platform-specific config needed (created by hierarchy template when Apple targets enabled)
    }
}
