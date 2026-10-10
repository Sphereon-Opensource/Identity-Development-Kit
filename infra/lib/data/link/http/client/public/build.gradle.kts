import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
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
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                compilerOptions {
                    moduleKind = JsModuleKind.MODULE_ES
                    target = "es2015"
                }
                browser { testTask { enabled = false } }
                nodejs { testTask { useMocha { timeout = "60000" } } }
                binaries.library()
                generateTypeScriptDefinitions()
            }
        }
    }

    configureWasmJsTargetIfEnabled {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    configureIosTargetsIfEnabled()
    configureLinuxTargetIfEnabled()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-compat-annotations:$version" else project(":lib-core-compat-annotations"))
                api(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-public:$version" else project(":lib-core-api-public"))
                api(sphereonlib.io.ktor.client.core)
                api(sphereonlib.io.ktor.client.content.negotiation)
                api(sphereonlib.io.ktor.client.logging)
                api(sphereonlib.io.ktor.serialization.kotlinx.json)
                api(sphereonlib.io.ktor.serialization.kotlinx.cbor)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(sphereonlib.io.ktor.client.cio.jvm)
                api(sphereonlib.io.ktor.client.okhttp.jvm)
            }
        }
        findByName("jsMain")?.dependencies {
            api(sphereonlib.io.ktor.client.cio.js)
        }
        findByName("wasmJsMain")?.dependencies {
            api(sphereonlib.io.ktor.client.cio.wasm.js)
        }
        findByName("appleMain")?.dependencies {
            api(sphereonlib.io.ktor.client.darwin)
        }
        findByName("linuxMain")?.dependencies {
            api(sphereonlib.io.ktor.client.cio)
        }
    }
}
