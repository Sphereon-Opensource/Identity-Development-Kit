import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.npm.publish.org.jetbrains.kotlin.npm.publish.gradle.plugin)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(libs.plugins.metro)
    id("maven-publish")
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
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

    js {
        outputModuleName = "@sphereon/kmp-lib-cbor-public"
        nodejs {
            useEsModules()
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
                api(projects.libCoreApiPublic)
                api(projects.libCoreCompat)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.cbor)
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
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.mockk)
            }
        }
        val jsMain by getting {
            dependencies {
            }
        }

        val jsTest by getting {
        }

        val nativeTest by getting {
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
                "name" by "@sphereon/kmp-cbor-public"
                "version" by rootProject.extra["npmVersion"] as String
            }
            scope.set("@sphereon")
            packageName.set("kmp-cbor-public")
        }
    }
}

// Replace wasmJs npm-publish tasks: mainFile provider has no value on Kotlin 2.3.x wasmJs targets
afterEvaluate {
    listOf("assembleWasmJsPackage", "packWasmJsPackage", "publishWasmJsPackageToNpmjsRegistry").forEach { taskName ->
        try { tasks.replace(taskName) } catch (_: Exception) {}
    }
}

// Test coverage configuration
kover {
    reports {
        // Exclude test classes, generated code, and third-party DI code from coverage
        filters {
            excludes {
                classes("*Test", "*Test\$*", "*.testing.*", "*\$\$*")
                // Exclude Amazon lastmile inject library code (third-party DI infrastructure)
                classes("amazon.lastmile.inject.*", "software.amazon.lastmile.kotlin.inject.*")
                // Exclude generated component implementations
                classes("*.create\$*", "*\$Companion\$create\$*")
                classes("*\$\$InjectClass", "*\$\$InjectModule")
                // Exclude Kotlin interface default method implementations
                classes("**\$DefaultImpls", "**\$DefaultImpls\$*")
            }
        }

        total {
            verify {
                onCheck = false
                rule("Minimum line coverage") {
                    minBound(95, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
                }
                rule("Minimum branch coverage") {
                    minBound(85, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
                }
            }
        }
    }
}
