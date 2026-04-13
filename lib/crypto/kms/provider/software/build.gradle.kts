import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.npm.publish.org.jetbrains.kotlin.npm.publish.gradle.plugin)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
//    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(libs.plugins.metro)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xenable-suspend-function-exporting")
                }
            }
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

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    iosX64 {
        binaries.all {
            linkerOpts("-framework", "Security")
        }
    }
    iosArm64 {
        binaries.all {
            linkerOpts("-framework", "Security")
        }
    }
    iosSimulatorArm64 {
        binaries.all {
            linkerOpts("-framework", "Security")
        }
    }

    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.libCoreApiPublic)
                implementation(sphereonlib.dev.whyoleg.cryptography.core)
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.optimal)
//                implementation(projects.libCoreApiDefault)
                implementation(libs.bundles.app.platform.di)

                implementation(projects.libCoreCompat)
                implementation(projects.libCborPublic)
                implementation(projects.libCryptoCore)

                // Impl because we directly instantiate a memory keystore
                implementation(projects.libCryptoCoreImpl)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.cbor)
//                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.io.core)
//                implementation(sphereonlib.dev.whyoleg.cryptography.core)
                implementation(sphereonlib.at.asitplus.signum.indispensable)
                implementation(sphereonlib.at.asitplus.signum.indispensable.asn1)
                implementation(sphereonlib.at.asitplus.signum.indispensable.josef)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libCoreApiDefault)
                implementation(projects.libDataLinkHttpClientImpl)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(libs.amz.metro.impl)
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(projects.libCoreApiDefault)
            }
        }
        val jsMain by getting {
            dependencies {
            }
        }

        val jsTest by getting {
            dependencies {
//                implementation(sphereonlib.io.kotest.assertions.core)
//                implementation(sphereonlib.io.kotest.framework.engine)

//                implementation(sphereonlib.io.kotest.property)
                implementation(projects.libCoreApiDefault)
                implementation(projects.libDataLinkHttpClientImpl)

            }
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
            }
        }
        val wasmJsTest by getting {
            dependencies {
                implementation(projects.libCoreApiDefault)
                implementation(projects.libDataLinkHttpClientImpl)
            }
        }
        val iosMain by getting {
            dependencies {

            }
        }
/*
        val iosTest by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                implementation(projects.libCoreApiDefault)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(libs.amz.metro.impl)
            }
        }*/

        val iosX64Test by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                implementation(projects.libCoreApiDefault)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(libs.amz.metro.impl)
            }
        }

        val iosSimulatorArm64Test by getting {
            dependencies {
                implementation(libs.bundles.app.platform.di)
                implementation(projects.libCoreApiDefault)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(libs.amz.metro.impl)
            }
        }
    }
}

npmPublish {
    registries {
        register("npmjs") {
            uri.set("https://registry.npmjs.org")
            authToken.set(System.getenv("NPM_TOKEN") ?: "")
        }
    }
    packages {
        named("js") {
            packageJson {
                "name" by "@sphereon/kmp-kms-ecdsa"
                "version" by rootProject.extra["npmVersion"] as String
            }
            scope.set("@sphereon")
            packageName.set("kmp-kms-ecdsa")
        }
    }
}

// Replace wasmJs npm-publish tasks: mainFile provider has no value on Kotlin 2.3.x wasmJs targets
afterEvaluate {
    listOf("assembleWasmJsPackage", "packWasmJsPackage", "publishWasmJsPackageToNpmjsRegistry").forEach { taskName ->
        try { tasks.replace(taskName) } catch (_: Exception) {}
    }
}

// Bump language/API version to 2.2 for @JsFun support in wasmJs AES-KW implementation
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}

