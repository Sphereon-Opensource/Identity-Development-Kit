import com.sphereon.gradle.plugin.configureStandardTargets
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val generatedPublicSuffixSources = layout.buildDirectory.dir("generated/sources/publicSuffix/commonMain/kotlin")

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.android.kotlin.multiplatform.library)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
}

kotlin {
    configureStandardTargets()
    android {
        namespace = "com.sphereon.wallet.party.local"
        compileSdk = 36
        minSdk = 27
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generatedPublicSuffixSources)
            dependencies {
                api(projects.libWalletPartyPublic)
                implementation(projects.libCoreIdnPublic)
                implementation(projects.libDataStoreKvPublic)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.libDataStoreKvImplMemory)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}

/**
 * Compiles the pinned publicsuffix.org snapshot into common Kotlin data. The complete ICANN and
 * PRIVATE sections are retained, so the runtime does not need a platform API or network access.
 */
val generateBundledPublicSuffixRules by tasks.registering {
    group = "build"
    description = "Generates the offline commonMain Public Suffix List rule bundle."

    val sourceFile = layout.projectDirectory.file("src/commonMain/resources/publicsuffix/public_suffix_list.dat").asFile
    val outputDir = generatedPublicSuffixSources.get().asFile
    val expectedSha256 = "348c18cc9cf86866917b50133c01dced82a329e29566337205ac816d36e1a72f"

    inputs.file(sourceFile)
    inputs.property("expectedSha256", expectedSha256)
    outputs.dir(outputDir)

    doLast {
        val actualSha256 =
            MessageDigest
                .getInstance("SHA-256")
                .digest(sourceFile.readBytes())
                .joinToString("") { "%02x".format(it) }
        require(actualSha256 == expectedSha256) {
            "Public Suffix List SHA-256 mismatch: expected=$expectedSha256 actual=$actualSha256"
        }

        val source = sourceFile.readText(Charsets.UTF_8)
        require("// ===BEGIN ICANN DOMAINS===" in source && "// ===END PRIVATE DOMAINS===" in source) {
            "Public Suffix List is incomplete"
        }
        val rules =
            source.lineSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("//") }
                .distinct()
                .toList()
        require(rules.size > 10_000) { "Public Suffix List rule count is unexpectedly small: ${rules.size}" }

        val chunks = mutableListOf<String>()
        var chunk = StringBuilder()
        rules.forEach { rule ->
            if (chunk.isNotEmpty() && chunk.length + rule.length + 1 > 12_000) {
                chunks += chunk.toString()
                chunk = StringBuilder()
            }
            if (chunk.isNotEmpty()) chunk.append('\n')
            chunk.append(rule)
        }
        if (chunk.isNotEmpty()) chunks += chunk.toString()

        outputDir.deleteRecursively()
        outputDir.mkdirs()
        val generated = outputDir.resolve("BundledPublicSuffixRules.kt")
        generated.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine("/* GENERATED from the pinned publicsuffix.org list. Do not edit. */")
            writer.appendLine("package com.sphereon.wallet.party.local")
            writer.appendLine()
            writer.appendLine("internal object BundledPublicSuffixRules {")
            writer.appendLine("    val chunks: List<String> = listOf(")
            chunks.forEach { value ->
                writer.appendLine("        \"\"\"")
                writer.appendLine(value)
                writer.appendLine("        \"\"\".trimIndent(),")
            }
            writer.appendLine("    )")
            writer.appendLine("}")
        }
    }
}

tasks.matching { task ->
    val name = task.name
    name.startsWith("compileKotlin") ||
        name.startsWith("compileAndroid") ||
        name.startsWith("compileCommonMain") ||
        name.startsWith("runKtlint") ||
        name.startsWith("detekt") ||
        name.endsWith("SourcesJar") ||
        name == "sourcesJar"
}.configureEach {
    dependsOn(generateBundledPublicSuffixRules)
}
