import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.npm.publish.org.jetbrains.kotlin.npm.publish.gradle.plugin)
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
        outputModuleName = "@sphereon/kmp-lib-crypto-core-public"
        nodejs {
            binaries.library()
            generateTypeScriptDefinitions()
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Core dependencies (no DI)
                api(projects.libCoreApiPublic)
                api(projects.libCoreCompat)
                api(projects.libCborPublic)
                api(sphereonlib.dev.whyoleg.cryptography.core)
                api(sphereonlib.dev.whyoleg.cryptography.provider.optimal)
                implementation(sphereonlib.org.kotlincrypto.core.digest)
                implementation(sphereonlib.org.kotlincrypto.hash.sha1)
                implementation(sphereonlib.org.kotlincrypto.hash.sha2)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.cbor)
                implementation(sphereonlib.org.jetbrains.kotlinx.io.core)
                api(sphereonlib.at.asitplus.signum.indispensable)
                api(sphereonlib.at.asitplus.signum.indispensable.asn1)
                api(sphereonlib.at.asitplus.signum.indispensable.josef)
                implementation(sphereonlib.io.ktor.client.core)
                // DI annotations
                implementation(libs.bundles.app.platform.di)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.jdk)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("@js-joda/core", "5.6.3"))
                implementation(npm("@js-joda/timezone", "2.22.0"))
                api(sphereonlib.dev.whyoleg.cryptography.provider.webcrypto)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                api(sphereonlib.dev.whyoleg.cryptography.provider.webcrypto)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val appleMain by getting {
            dependencies {
                api(sphereonlib.io.ktor.client.darwin)
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
                "name" by "@sphereon/kmp-crypto-core-public"
                "version" by rootProject.extra["npmVersion"] as String
            }
            scope.set("@sphereon")
            packageName.set("kmp-crypto-core-public")
        }
    }
}

// Replace wasmJs npm-publish tasks: mainFile provider has no value on Kotlin 2.3.x wasmJs targets
afterEvaluate {
    listOf("assembleWasmJsPackage", "packWasmJsPackage", "publishWasmJsPackageToNpmjsRegistry").forEach { taskName ->
        try { tasks.replace(taskName) } catch (_: Exception) {}
    }
}
