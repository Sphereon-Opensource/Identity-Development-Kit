import com.sphereon.gradle.plugin.configureStandardTargets
import java.net.URL

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}

// ================================================================================================
// OKD spec download from upstream GitHub repo
// ================================================================================================

val okdSpecVersion = "main"
val okdSpecBaseUrl = "https://raw.githubusercontent.com/Onderwijs-Koppelingen-OKx/OKD-Document-Management/$okdSpecVersion/specification/v5"
val okdSpecDir = layout.buildDirectory.dir("okd-spec/v5").get().asFile

val specFiles = listOf(
    "spec.yaml",
    "schemas/DocumentMetadata.yaml",
    "schemas/Person.yaml",
    "schemas/PersonId.yaml",
    "schemas/PersonProperties.yaml",
    "schemas/PrimaryCode.yaml",
    "schemas/OtherCodes.yaml",
    "schemas/Association.yaml",
    "schemas/AssociationFull.yaml",
    "schemas/AssociationPatch.yaml",
    "schemas/Offering.yaml",
    "schemas/OfferingFull.yaml",
    "schemas/DocumentUploadResponse.yaml",
    "schemas/Pagination.yaml",
    "schemas/ServiceMetadata.yaml",
    "schemas/ErrorBadRequest.yaml",
    "schemas/ErrorUnauthorized.yaml",
    "schemas/ErrorForbidden.yaml",
    "schemas/ErrorNotFound.yaml",
    "schemas/ErrorMethodNotAllowed.yaml",
    "schemas/ErrorTooManyRequests.yaml",
    "schemas/ErrorInternalServerError.yaml",
    "schemas/ErrorUnprocessableContent.yaml",
    "schemas/ErrorPayloadTooLarge.yaml",
    "paths/Service.yaml",
    "paths/PersonCollection.yaml",
    "paths/PersonInstance.yaml",
    "paths/OfferingInstance.yaml",
    "paths/AssociationInstance.yaml",
    "paths/DocumentInstance.yaml",
    "paths/DocumentInstanceMetadata.yaml",
    "parameters/primaryCode.yaml",
    "enumerations/gender.yaml",
)

// Clone/update the OKD spec repo via sparse checkout (specification/v5 only)
val okdRepoDir = layout.buildDirectory.dir("okd-repo").get().asFile

val downloadOkdSpec by tasks.registering(Exec::class) {
    description = "Clone the OKD specification from GitHub (sparse checkout, specification/v5 only)"
    outputs.dir(okdSpecDir)
    onlyIf { !File(okdSpecDir, "spec.yaml").exists() }

    doFirst { okdRepoDir.mkdirs() }

    val script = """
        if [ ! -d "${okdRepoDir.path.replace("\\", "/")}/.git" ]; then
            git clone --depth 1 --filter=blob:none --sparse https://github.com/Onderwijs-Koppelingen-OKx/OKD-Document-Management.git "${okdRepoDir.path.replace("\\", "/")}"
            cd "${okdRepoDir.path.replace("\\", "/")}"
            git sparse-checkout set specification/v5
        else
            cd "${okdRepoDir.path.replace("\\", "/")}"
            git pull --depth 1 2>/dev/null || true
        fi
        mkdir -p "${okdSpecDir.path.replace("\\", "/")}"
        cp -r "${okdRepoDir.path.replace("\\", "/")}/specification/v5/"* "${okdSpecDir.path.replace("\\", "/")}"
    """.trimIndent()

    commandLine("bash", "-c", script)
}

// ================================================================================================
// Bundle $ref-based spec into single file using redocly, then generate Kotlin models
// using openapi-generator-cli. Both run as external processes to avoid Gradle's
// Jackson version conflicts with the openapi-generator Gradle plugin.
// ================================================================================================

val kotlinBasePackage = "com.sphereon.data.store.okd.generated"
val kotlinModelPackage = "$kotlinBasePackage.models"
val generatedSourcesDir = layout.buildDirectory.dir("generated/openapi/src/commonMain/kotlin").get().asFile
val bundledSpecFile = File(okdSpecDir, "spec-bundled.yaml")
val templatesPath = file("${rootProject.projectDir}/lib/crypto/kms/rest/api/src/openapi/templates").absolutePath

val bundleOkdSpec by tasks.registering(Exec::class) {
    dependsOn(downloadOkdSpec)
    description = "Bundle OKD spec ${'$'}ref references into a single YAML file"
    inputs.dir(okdSpecDir)
    outputs.file(bundledSpecFile)

    commandLine(
        if (org.gradle.internal.os.OperatingSystem.current().isWindows) "npx.cmd" else "npx", "--yes", "@redocly/cli", "bundle",
        File(okdSpecDir, "spec.yaml").path,
        "--force",
        "-o", bundledSpecFile.path,
    )
}

val generateOkdModels by tasks.registering(Exec::class) {
    dependsOn(bundleOkdSpec)
    description = "Generate Kotlin models from bundled OKD OpenAPI spec"
    inputs.file(bundledSpecFile)
    outputs.dir(generatedSourcesDir)

    commandLine(
        if (org.gradle.internal.os.OperatingSystem.current().isWindows) "npx.cmd" else "npx", "--yes", "@openapitools/openapi-generator-cli", "generate",
        "-i", bundledSpecFile.path,
        "-g", "kotlin",
        "--library", "multiplatform",
        "-o", layout.buildDirectory.dir("generated/openapi").get().asFile.path,
        "-t", templatesPath,
        "--global-property", "models,supportingFiles=",
        "--model-package", kotlinModelPackage,
        "--package-name", kotlinBasePackage,
        "--additional-properties", "dateLibrary=kotlinx-datetime,collectionType=array",
        "--type-mappings", "kotlin.Any=kotlinx.serialization.json.JsonElement",
        "--skip-validate-spec",
    )

    // Fix known OpenAPI generator issues with Kotlin multiplatform output:
    // 1. Remove unused imports for unresolved oneOf wrapper types (OneOfLessThan...GreaterThan)
    // 2. Fix data classes that incorrectly extend HashMap (additionalProperties pattern)
    doLast {
        generatedSourcesDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            var content = file.readText()
            var modified = false

            // Remove imports for non-existent OneOf wrapper types
            val oneOfImportRegex = Regex("import .*\\.OneOfLessThan.*GreaterThan\\s*\\n")
            if (oneOfImportRegex.containsMatchIn(content)) {
                content = oneOfImportRegex.replace(content, "")
                modified = true
            }

            // Fix `) : kotlin.collections.HashMap<...>()() {` → `) {`
            // Data classes can't extend HashMap, and the double `()()` is a codegen bug
            val hashMapExtendRegex = Regex("""\)\s*:\s*kotlin\.collections\.HashMap<[^>]*>\(\)\(\)\s*\{""")
            if (hashMapExtendRegex.containsMatchIn(content)) {
                content = hashMapExtendRegex.replace(content, ") {")
                modified = true
            }

            if (modified) {
                file.writeText(content)
                logger.lifecycle("Fixed: ${file.name}")
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
        dependsOn(generateOkdModels)
    }
    tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
        if (name.contains("SourcesJar", ignoreCase = true)) {
            dependsOn(generateOkdModels)
        }
    }
}
