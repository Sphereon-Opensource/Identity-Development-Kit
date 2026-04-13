import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    alias(sphereonplug.plugins.org.openapi.generator)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    id("maven-publish")
}
metro {
}

val kotlinBasePackage = "com.sphereon.crypto.kms.rest.api.generated"
val kotlinModelPackage = "$kotlinBasePackage.models"
val kotlinApiPackage = "$kotlinBasePackage.api"
val inputSpecPath =
    layout.projectDirectory
        .file("src/openapi/openapi.yml")
        .asFile.path
val generatedSourcesPath =
    layout.buildDirectory
        .dir("generated/openapi")
        .get()
        .asFile.path
val templatesPath =
    layout.projectDirectory
        .dir("src/openapi/templates")
        .asFile.absolutePath

kotlin {
    kotlin.applyDefaultHierarchyTemplate()
    val openApiGenerateKotlin by tasks.registering(GenerateTask::class) {
        generatorName.set("kotlin")
        library.set("multiplatform")
        inputSpec.set(inputSpecPath)
        outputDir.set(generatedSourcesPath)
        templateDir.set(templatesPath)

        globalProperties.set(
            mapOf(
                "models" to "",
                "apis" to "false",
                "supportingFiles" to "",
            ),
        )
        configOptions.set(
            mapOf(
                "dateLibrary" to "kotlinx-datetime",
                "collectionType" to "array",
            ),
        )
        typeMappings.apply {
            put("kotlin.Any", "kotlinx.serialization.json.JsonElement")
        }
        packageName.set(kotlinBasePackage)
        modelPackage.set(kotlinModelPackage)
        apiPackage.set(kotlinApiPackage)
        cleanupOutput.set(true)
    }

    jvm {
        tasks.named<Jar>("jvmJar") {
            archiveBaseName.set("openapi")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            from(
                layout.buildDirectory
                    .dir("classes/kotlin/jvm/main")
                    .get()
                    .asFile.path,
            )
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
                binaries.library()
                generateTypeScriptDefinitions()
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
            kotlin.srcDir("$generatedSourcesPath/src/commonMain/kotlin")

            dependencies {
                api(projects.libCryptoCorePublic)
                api(sphereonlib.io.ktor.client.core)
                api(sphereonlib.io.ktor.client.content.negotiation)
                api(sphereonlib.io.ktor.client.serialization)
                api(sphereonlib.io.ktor.serialization.kotlinx.json)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(libs.bundles.app.platform.di)
            }
        }

        // iosMain: no platform-specific config needed (created by hierarchy template when iOS targets enabled)
        val jvmMain by getting {
            dependencies {
                implementation(sphereonlib.io.ktor.client.cio)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            // opt in to hide warnings for the generated code
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=kotlin.io.encoding.ExperimentalEncodingApi",
            "-opt-in=kotlin.time.ExperimentalTime",
        )
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        dependsOn(openApiGenerateKotlin)
    }

    // Ensure source jar tasks also depend on the OpenAPI generation
    tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
        if (name.contains("SourcesJar", ignoreCase = true)) {
            dependsOn(openApiGenerateKotlin)
        }
    }
}
