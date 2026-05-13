import com.sphereon.gradle.plugin.configureStandardTargets
import java.net.URL

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}

// ================================================================================================
// OKD spec — checked-in under src/commonMain/resources/okd-spec/v5
//
// To update the spec files from upstream, run:
//   curl -sL "https://raw.githubusercontent.com/Onderwijs-Koppelingen-OKx/OKD-Document-Management/main/specification/v5/<file>" -o "src/commonMain/resources/okd-spec/v5/<file>"
// ================================================================================================

val okdSpecDir = file("src/commonMain/resources/okd-spec/v5")

// ================================================================================================
// Bundle $ref-based spec into single file using redocly, then generate Kotlin models
// using openapi-generator-cli. Both run as external processes to avoid Gradle's
// Jackson version conflicts with the openapi-generator Gradle plugin.
// ================================================================================================

val kotlinBasePackage = "com.sphereon.data.store.okd.generated"
val kotlinModelPackage = "$kotlinBasePackage.models"
val generatedSourcesDir =
    layout.buildDirectory
        .dir("generated/openapi/src/commonMain/kotlin")
        .get()
        .asFile
val bundledSpecFile =
    layout.buildDirectory
        .file("okd-spec/spec-bundled.yaml")
        .get()
        .asFile
val templatesPath = rootProject.projectDir.resolve("lib/crypto/kms/rest/api/src/openapi/templates").absolutePath
// Use paths relative to projectDir to avoid whitespace splitting in the @openapitools/openapi-generator-cli
// npm wrapper when the absolute path contains spaces (e.g. ".../Sphereon IDTech/...").
val bundledSpecRelative = bundledSpecFile.relativeTo(projectDir).path
val generatedOpenapiRelative = layout.buildDirectory.dir("generated/openapi").get().asFile.relativeTo(projectDir).path
val templatesRelative = rootProject.projectDir.resolve("lib/crypto/kms/rest/api/src/openapi/templates").relativeTo(projectDir).path
val okdSpecYamlRelative = File(okdSpecDir, "spec.yaml").relativeTo(projectDir).path

val bundleOkdSpec by tasks.registering(Exec::class) {
    description = "Bundle OKD spec ${'$'}ref references into a single YAML file"
    inputs.dir(okdSpecDir)
    outputs.file(bundledSpecFile)

    commandLine(
        if (org.gradle.internal.os.OperatingSystem
                .current()
                .isWindows
        ) {
            "npx.cmd"
        } else {
            "npx"
        },
        "--yes",
        "@redocly/cli",
        "bundle",
        okdSpecYamlRelative,
        "--force",
        "-o",
        bundledSpecRelative,
    )
}

val generateOkdModels by tasks.registering(Exec::class) {
    dependsOn(bundleOkdSpec)
    description = "Generate Kotlin models from bundled OKD OpenAPI spec"
    inputs.file(bundledSpecFile)
    outputs.dir(generatedSourcesDir)

    commandLine(
        if (org.gradle.internal.os.OperatingSystem
                .current()
                .isWindows
        ) {
            "npx.cmd"
        } else {
            "npx"
        },
        "--yes",
        "@openapitools/openapi-generator-cli",
        "generate",
        "-i",
        bundledSpecRelative,
        "-g",
        "kotlin",
        "--library",
        "multiplatform",
        "-o",
        generatedOpenapiRelative,
        "-t",
        templatesRelative,
        "--global-property",
        "models,supportingFiles=",
        "--model-package",
        kotlinModelPackage,
        "--package-name",
        kotlinBasePackage,
        "--additional-properties",
        "dateLibrary=kotlinx-datetime,collectionType=array",
        "--type-mappings",
        "kotlin.Any=kotlinx.serialization.json.JsonElement",
        "--skip-validate-spec",
    )

    // Fix known OpenAPI generator issues in a separate task to avoid doLast closure
}

// Separate task for post-processing generated code (CC-safe — no Project references in action)
val fixGeneratedCode by tasks.registering {
    dependsOn(generateOkdModels)
    val srcDir = generatedSourcesDir
    inputs.dir(srcDir)
    outputs.dir(srcDir)

    doLast {
        srcDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            var content = file.readText()
            var modified = false

            val oneOfImportRegex = Regex("import .*\\.OneOfLessThan.*GreaterThan\\s*\\n")
            if (oneOfImportRegex.containsMatchIn(content)) {
                content = oneOfImportRegex.replace(content, "")
                modified = true
            }

            val hashMapExtendRegex = Regex("""\)\s*:\s*kotlin\.collections\.HashMap<[^>]*>\(\)\(\)\s*\{""")
            if (hashMapExtendRegex.containsMatchIn(content)) {
                content = hashMapExtendRegex.replace(content, ") {")
                modified = true
            }

            if (modified) {
                file.writeText(content)
                println("Fixed: ${file.name}")
            }
        }
    }
}

// ================================================================================================
// KMP module setup
// ================================================================================================

kotlin {
    configureStandardTargets()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generatedSourcesDir)

            dependencies {
                api(projects.libCoreApiPublic)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                api(sphereonlib.org.jetbrains.kotlinx.datetime)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=kotlin.io.encoding.ExperimentalEncodingApi",
            "-opt-in=kotlin.time.ExperimentalTime",
        )
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        dependsOn(fixGeneratedCode)
    }
    tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
        if (name.contains("SourcesJar", ignoreCase = true)) {
            dependsOn(fixGeneratedCode)
        }
    }
}
