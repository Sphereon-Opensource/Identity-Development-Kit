import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    id("maven-publish")
}
metro {
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
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                compilerOptions {
                    moduleKind = JsModuleKind.MODULE_ES
                    target = "es2015"
                }
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
        }
    }

    configureIosTargetsIfEnabled()
    configureLinuxTargetIfEnabled()

    configureWasmJsTargetIfEnabled {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-compat-annotations:$version" else project(":lib-core-compat-annotations"))
                implementation(libs.bundles.app.platform.di)
                api(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-trust-core-public:$version" else project(":lib-trust-core-public"))
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-trust-core-impl:$version" else project(":lib-trust-core-impl"))
                api(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-trust-etsi-entities-public:$version" else project(":lib-trust-etsi-entities-public"))
                api(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-public:$version" else project(":lib-core-api-public"))
                api(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core-public:$version" else project(":lib-crypto-core-public"))
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
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-core-impl:$version" else project(":lib-crypto-core-impl"))
                implementation(if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") "com.sphereon.idk:lib-core-api-default:$version" else project(":lib-core-api-default"))
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-crypto-kms-provider-software:$version" else project(":lib-crypto-kms-provider-software"))
                implementation(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-data-link-http-client-impl:$version" else project(":lib-data-link-http-client-impl"))
                implementation(if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") "com.sphereon.idk:lib-trust-x509:$version" else project(":lib-trust-x509"))
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(sphereonlib.io.ktor.client.cio.jvm)
                implementation(sphereonlib.org.bouncycastle.bcprov.jdk18on)
                implementation(sphereonlib.org.bouncycastle.bcpkix.jdk18on)
            }
        }
        findByName("jsMain")?.dependencies {
            implementation(sphereonlib.software.amazon.app.platform.metro.impl)
        }
        findByName("jsTest")?.dependencies {
            implementation(sphereonlib.io.kotest.assertions.core)
            implementation(sphereonlib.io.kotest.framework.engine)
            implementation(sphereonlib.io.kotest.property)
            implementation(npm("jsdom", "~25.0.1"))
        }
        findByName("appleMain")?.dependencies {
            api(sphereonlib.io.ktor.client.darwin)
        }
    }
}
