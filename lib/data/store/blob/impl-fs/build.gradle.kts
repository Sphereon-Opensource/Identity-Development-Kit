import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.metro)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
    jvm()
    js {
        compilerOptions {
            moduleKind = JsModuleKind.MODULE_ES
            target = "es2015"
        }
        browser {
            testTask { useMocha { timeout = "60000" } }
        }
        nodejs {
            testTask { useMocha { timeout = "60000" } }
        }
        binaries.library()
        generateTypeScriptDefinitions()
    }
    // wasmJs excluded: no filesystem support
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libDataStoreBlobPublic)
                implementation("com.squareup.okio:okio:3.9.1")
                implementation(libs.bundles.app.platform.di)
                api(libs.amz.metro.public)
                api(libs.amz.metro.impl)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("com.squareup.okio:okio-nodefilesystem:3.9.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(libs.amz.metro.impl)
                implementation("com.squareup.okio:okio-fakefilesystem:3.9.1")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCoreApiDefault)
            }
        }
    }
}

