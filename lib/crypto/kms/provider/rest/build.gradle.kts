import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
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
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                nodejs {
                    testTask {
                        useMocha {
                            timeout = "40000"
                        }
                    }
                }
             /*   browser {
                    testTask {
                        useMocha {
                            timeout = "40000"
                        }
                    }
                }*/

                binaries.library()
                generateTypeScriptDefinitions()
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCorePublic)
                api(projects.libCryptoKmsRestApi)
                api(projects.libDataLinkHttpClientPublic)
                implementation(sphereonlib.org.jetbrains.kotlinx.io.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCoreApiDefault)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.jdk)
                implementation(projects.libCryptoCoreImpl)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(projects.libCoreApiDefault)
            }
        }
        findByName("jsTest")?.dependencies {
            implementation(sphereonlib.io.kotest.assertions.core)
            implementation(sphereonlib.io.kotest.framework.engine)

            implementation(sphereonlib.io.kotest.property)
            implementation(sphereonlib.dev.whyoleg.cryptography.provider.webcrypto)
            implementation(projects.libCoreApiDefault)
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.AbstractTestTask>().configureEach { failOnNoDiscoveredTests = false }
