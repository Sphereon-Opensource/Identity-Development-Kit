import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(libs.plugins.metro)
    id("maven-publish")
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()

    // Allow internal API usage from xmlutil library
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
    js {
        compilerOptions {
            moduleKind = JsModuleKind.MODULE_ES
            target = "es2015"
        }
        outputModuleName = "@sphereon/kmp-trust-etsi"
        nodejs {
            binaries.library()
            generateTypeScriptDefinitions()
            testTask {
                useMocha {
                    timeout = "10s"
                }
                environment("PROJECT_DIR", projectDir.absolutePath)
            }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                api(projects.libTrustCorePublic)
                implementation(projects.libTrustCoreImpl)
                api(projects.libTrustEtsiEntitiesPublic)
                api(projects.libCoreApiPublic)
                api(projects.libCoreCompat)
                api(projects.libCryptoCorePublic)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.datetime)
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.github.pdvrieze.xmlutil.core)
                implementation(sphereonlib.io.github.pdvrieze.xmlutil.serialization)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoKmsProviderSoftware)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(libs.amz.metro.impl)
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.io.ktor.client.cio.jvm)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(libs.amz.metro.impl)
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(sphereonlib.io.kotest.assertions.core)
                implementation(sphereonlib.io.kotest.framework.engine)
                implementation(sphereonlib.io.kotest.property)
                implementation(npm("jsdom", "~25.0.1"))
            }
        }
        val appleMain by getting {
            dependencies {
                api(sphereonlib.io.ktor.client.darwin)
            }
        }
    }
}
