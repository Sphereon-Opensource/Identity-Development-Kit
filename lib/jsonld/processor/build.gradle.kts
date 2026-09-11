import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    id("maven-publish")
}

metro {}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()
    jvm()
    run {
        val targets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in targets || "js" in targets) {
            js { nodejs(); binaries.library(); generateTypeScriptDefinitions() }
        }
    }
    configureWasmJsTargetIfEnabled { nodejs(); binaries.library(); generateTypeScriptDefinitions() }
    configureIosTargetsIfEnabled()
    configureLinuxTargetIfEnabled()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libJsonldLoader)
                api(projects.libJsonldRdfCanon)
                api(projects.libCoreApiPublic)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}
