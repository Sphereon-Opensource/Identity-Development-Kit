/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled
import java.security.MessageDigest

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

    jvm()
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                nodejs()
                binaries.library()
                generateTypeScriptDefinitions()
            }
        }
    }
    configureWasmJsTargetIfEnabled {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    configureIosTargetsIfEnabled()
    configureLinuxTargetIfEnabled()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/sources/bundledContexts/commonMain/kotlin"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/sources/bundledSchemas/commonMain/kotlin"))
            dependencies {
                api(projects.libJsonldPublic)
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCore)
                api(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.core)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)

                // KMP-native JSON Schema validator (Draft 2020-12). MIT-licensed.
                // Used by ValidateJsonLdSchemaServiceCommandImpl.
                implementation(sphereonlib.io.github.optimumcode.json.schema.validator)

                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(projects.libCoreApiDefault)
            }
        }
    }
}

// Reads commonMain/resources/contexts/built-in-contexts.json plus the
// referenced .jsonld files and emits BundledContexts.kt — a Kotlin object
// exposing iri -> raw-JSON Map. Verifies each file's SHA-256 against the
// manifest at build time so a tampered or stale resource fails the build.
//
// Configuration-cache compatible: the doLast captures only File values, no
// Gradle Project / DirectoryProperty references.
val generateBundledContexts by tasks.registering {
    group = "build"
    description = "Generates BundledContexts.kt from the built-in @context resource files."

    val resourceDirFile = layout.projectDirectory.dir("src/commonMain/resources/contexts").asFile
    val outputDirFile =
        layout.buildDirectory
            .dir("generated/sources/bundledContexts/commonMain/kotlin")
            .get()
            .asFile

    inputs.dir(resourceDirFile)
    outputs.dir(outputDirFile)

    doLast {
        val manifestFile = resourceDirFile.resolve("built-in-contexts.json")
        val manifest = groovy.json.JsonSlurper().parse(manifestFile) as Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val entries = manifest["entries"] as List<Map<String, String>>

        outputDirFile.deleteRecursively()
        outputDirFile.mkdirs()

        val sb = StringBuilder()
        sb.appendLine("/*")
        sb.appendLine(" * GENERATED FILE — do not edit by hand.")
        sb.appendLine(" * Regenerate via './gradlew :lib-jsonld-loader:generateBundledContexts'.")
        sb.appendLine(" * Source manifest: src/commonMain/resources/contexts/built-in-contexts.json")
        sb.appendLine(" */")
        sb.appendLine("package com.sphereon.jsonld.loader.bundled")
        sb.appendLine()
        sb.appendLine("internal object BundledContexts {")
        sb.appendLine("    /** IRI -> raw JSON-LD context body, as fetched from upstream. */")
        sb.appendLine("    val ENTRIES: Map<String, String> = mapOf(")

        entries.forEachIndexed { idx, entry ->
            val iri = entry["iri"] ?: error("manifest entry missing 'iri'")
            val file = entry["file"] ?: error("manifest entry missing 'file'")
            val expectedSha = entry["sha256"] ?: error("manifest entry missing 'sha256'")
            val payload = resourceDirFile.resolve(file).readBytes()
            val actualSha =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(payload)
                    .joinToString("") { "%02x".format(it) }
            require(actualSha == expectedSha) {
                "SHA-256 mismatch for $file: manifest=$expectedSha actual=$actualSha"
            }
            val escaped = payload.toString(Charsets.UTF_8).replace("\$", "\${'\$'}")
            sb.append("        \"")
            sb.append(iri.replace("\\", "\\\\").replace("\"", "\\\""))
            sb.append("\" to \"\"\"")
            sb.append(escaped)
            sb.append("\"\"\"")
            sb.appendLine(if (idx < entries.lastIndex) "," else ",")
        }

        sb.appendLine("    )")
        sb.appendLine("}")

        outputDirFile.resolve("BundledContexts.kt").writeText(sb.toString(), Charsets.UTF_8)
    }
}

// Reads commonMain/resources/schemas/built-in-schemas.json plus the
// referenced UNTP 0.7.0 *.json files and emits BundledSchemas.kt —
// credentialType -> (schemaUri, raw-JSON-body) map. SHA-256 verified at
// build time, same shape as BundledContexts above.
val generateBundledSchemas by tasks.registering {
    group = "build"
    description = "Generates BundledSchemas.kt from the bundled UNTP JSON Schema resource files."

    val resourceDirFile = layout.projectDirectory.dir("src/commonMain/resources/schemas").asFile
    val outputDirFile =
        layout.buildDirectory
            .dir("generated/sources/bundledSchemas/commonMain/kotlin")
            .get()
            .asFile

    inputs.dir(resourceDirFile)
    outputs.dir(outputDirFile)

    doLast {
        val manifestFile = resourceDirFile.resolve("built-in-schemas.json")
        val manifest = groovy.json.JsonSlurper().parse(manifestFile) as Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val entries = manifest["entries"] as List<Map<String, String>>

        outputDirFile.deleteRecursively()
        outputDirFile.mkdirs()

        val sb = StringBuilder()
        sb.appendLine("/*")
        sb.appendLine(" * GENERATED FILE — do not edit by hand.")
        sb.appendLine(" * Regenerate via './gradlew :lib-jsonld-loader:generateBundledSchemas'.")
        sb.appendLine(" * Source manifest: src/commonMain/resources/schemas/built-in-schemas.json")
        sb.appendLine(" */")
        sb.appendLine("package com.sphereon.jsonld.loader.bundled")
        sb.appendLine()
        sb.appendLine("internal object BundledSchemas {")
        sb.appendLine("    /** A bundled JSON Schema entry: the IRI it was sourced from + the raw body. */")
        sb.appendLine("    internal data class Entry(val schemaUri: String, val body: String)")
        sb.appendLine()
        sb.appendLine("    /** credentialType -> bundled-schema entry. */")
        sb.appendLine("    val ENTRIES: Map<String, Entry> = mapOf(")

        entries.forEachIndexed { idx, entry ->
            val credentialType = entry["credentialType"] ?: error("manifest entry missing 'credentialType'")
            val file = entry["file"] ?: error("manifest entry missing 'file'")
            val expectedSha = entry["sha256"] ?: error("manifest entry missing 'sha256'")
            val schemaUri = entry["schemaUri"] ?: error("manifest entry missing 'schemaUri'")
            val payload = resourceDirFile.resolve(file).readBytes()
            val actualSha =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(payload)
                    .joinToString("") { "%02x".format(it) }
            require(actualSha == expectedSha) {
                "SHA-256 mismatch for $file: manifest=$expectedSha actual=$actualSha"
            }
            val escapedBody = payload.toString(Charsets.UTF_8).replace("\$", "\${'\$'}")
            sb.append("        \"")
            sb.append(credentialType.replace("\\", "\\\\").replace("\"", "\\\""))
            sb.append("\" to Entry(\n            schemaUri = \"")
            sb.append(schemaUri.replace("\\", "\\\\").replace("\"", "\\\""))
            sb.append("\",\n            body = \"\"\"")
            sb.append(escapedBody)
            sb.append("\"\"\",\n        )")
            sb.appendLine(if (idx < entries.lastIndex) "," else ",")
        }

        sb.appendLine("    )")
        sb.appendLine("}")

        outputDirFile.resolve("BundledSchemas.kt").writeText(sb.toString(), Charsets.UTF_8)
    }
}

// Every Kotlin compile and ktlint check depends on both generators.
// configureEach is configuration-cache friendly when the closure body is
// light. ktlint tasks read the generated sources too — without declaring
// the dependency Gradle 8+ flags an implicit-input error.
val readsGeneratedSources: (org.gradle.api.Task) -> Boolean = { task ->
    val name = task.name
    name.startsWith("compileKotlin") ||
        name.startsWith("compileCommonMain") ||
        name.startsWith("runKtlintCheck") ||
        name.startsWith("runKtlintFormat")
}
tasks.matching(readsGeneratedSources).configureEach {
    dependsOn(generateBundledContexts)
    dependsOn(generateBundledSchemas)
}
