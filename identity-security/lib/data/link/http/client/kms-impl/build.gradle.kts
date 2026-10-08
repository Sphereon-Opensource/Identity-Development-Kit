import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
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
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                compilerOptions {
                    moduleKind = JsModuleKind.MODULE_ES
                    target = "es2015"
                }
                nodejs {
                    testTask {
                        useMocha {
                            timeout = "40000"
                        }
                    }
                }
                binaries.library()
                generateTypeScriptDefinitions()
            }
        }
    }

    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "ios" in kmpTargets) {
            iosArm64 { binaries.all { linkerOpts("-framework", "Security") } }
            iosSimulatorArm64 { binaries.all { linkerOpts("-framework", "Security") } }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Always the pack-local public API (includeLocal from infra). Artifact mode would
                // resolve a pre-Wave-1 jar and miss HttpClientBindingPriority / logging helpers.
                api(project(":lib-data-link-http-client-public"))
                implementation(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-http-client-impl:$version" else project(":lib-data-link-http-client-impl"))
                api(
                    if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") {
                        "com.sphereon.idk:lib-crypto-core-public:$version"
                    } else {
                        project(":lib-crypto-core-public")
                    },
                )
                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                // Metro 1.4.4 annotation API (priority=); do not let older App Platform transitively win.
                api("dev.zacsweers.metro:runtime:1.4.4")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(sphereonlib.io.ktor.client.okhttp.jvm)
                // Software keystore platform types used for mTLS / keystore-backed CAs.
                api(
                    if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") {
                        "com.sphereon.idk:lib-crypto-kms-provider-software:$version"
                    } else {
                        project(":lib-crypto-kms-provider-software")
                    },
                )
                api(
                    if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") {
                        "com.sphereon.idk:lib-crypto-core:$version"
                    } else {
                        project(":lib-crypto-core")
                    },
                )
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(
                    if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") {
                        "com.sphereon.idk:lib-core-api-default:$version"
                    } else {
                        project(":lib-core-api-default")
                    },
                )
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
        findByName("appleMain")?.dependencies {
            api(sphereonlib.io.ktor.client.darwin)
        }
    }
}
