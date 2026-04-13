import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.npm.publish.org.jetbrains.kotlin.npm.publish.gradle.plugin)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.io.kotest.io.kotest.gradle.plugin)
    alias(sphereonplug.plugins.com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin)
    alias(libs.plugins.metro)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
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

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.libCoreApiPublic)
                api(projects.libCoreCompat)
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
                implementation(libs.amz.metro.impl)
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
        val jsMain by getting {
            dependencies {
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(sphereonlib.io.kotest.assertions.core)
                implementation(sphereonlib.io.kotest.framework.engine)

                implementation(sphereonlib.io.kotest.property)
                implementation(sphereonlib.dev.whyoleg.cryptography.provider.webcrypto)
                implementation(projects.libCoreApiDefault)

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
                "name" by "@sphereon/kmp-kms-provider-rest"
                "version" by rootProject.extra["npmVersion"] as String
            }
            scope.set("@sphereon")
            packageName.set("kmp-kms-provider-rest")
        }
    }
}


tasks.withType<org.gradle.api.tasks.testing.AbstractTestTask>().configureEach { failOnNoDiscoveredTests = false }
