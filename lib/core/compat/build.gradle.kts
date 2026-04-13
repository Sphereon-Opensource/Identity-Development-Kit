@file:OptIn(ExperimentalSwiftExportDsl::class, ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl

plugins {
//    alias(libs.plugins.androidLibrary)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
//    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
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

    // Allow internal API usage from xmlutil library (needed for DomWriter in tests)
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
    /*  androidTarget {
          publishLibraryVariants("release")
          compilations.all {
              kotlinOptions {
                  jvmTarget = JavaVersion.VERSION_17.toString()
              }
          }
      }*/
    js {
        compilerOptions {
            moduleKind = JsModuleKind.MODULE_ES
            target = "es2015"
        }
        outputModuleName = "@sphereon/kmp-compat"
        browser {
            testTask { useMocha { timeout = "60000" } }
        }
        nodejs {
            testTask { useMocha { timeout = "60000" } }
        }
        binaries.library()
        generateTypeScriptDefinitions()
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    wasmJs {
        browser()
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // default deps are already injected by conventions plugin!
                api(sphereonlib.org.jetbrains.kotlinx.datetime)
                kotlin("stdlib")
                api(sphereonlib.io.matthewnelson.encoding.base64)
                api(sphereonlib.io.ktor.io)
                implementation(sphereonlib.io.github.pdvrieze.xmlutil.core)
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
        val jvmTest by getting
        val jsMain by getting {
            dependencies {
                implementation(npm("@js-joda/timezone", "2.22.0"))
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
        val wasmJsTest by getting {
            dependencies {
                implementation(npm("jsdom", "~25.0.1"))
            }
        }
        /* val nativeMain by getting {
             dependencies {}
         }
         val nativeTest by getting*/
    }

    swiftExport {
        // Set the root module name
        moduleName = "Compat"

        // Set the collapse rule
        // Removes package prefix from generated Swift code
        flattenPackage = "com.sphereon.core.compat"

        // Provide compiler arguments to link tasks
        configure {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
}

