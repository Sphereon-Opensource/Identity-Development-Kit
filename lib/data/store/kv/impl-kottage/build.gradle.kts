import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
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
    kotlin.applyDefaultHierarchyTemplate()

    jvm()
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                nodejs()
                binaries.library()
                generateTypeScriptDefinitions()
            }
        }
    }
    configureIosTargetsIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libDataStoreKvPublic)
                implementation(sphereonlib.io.github.irgaly.kottage.kottage)

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
                // Needed for DefaultRootScopeProvider + default context/session implementations in DI tests
                implementation(projects.libCoreApiDefault)
            }
        }
    }
}
