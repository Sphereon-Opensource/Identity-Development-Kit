import com.sphereon.gradle.plugin.configureIosTargetsIfEnabled
import com.sphereon.gradle.plugin.configureLinuxTargetIfEnabled
import com.sphereon.gradle.plugin.configureWasmJsTargetIfEnabled
import com.sphereon.gradle.plugin.openapiSpec
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}

metro {
}

val generatedFailureMessagesSources =
    layout.buildDirectory.dir("generated/sources/failureMessages/commonMain/kotlin")

val generatedAuthorizationHandoffUriVectorSources =
    layout.buildDirectory.dir("generated/sources/authorizationHandoffUriVectors/commonTest/kotlin")

kotlin {
    jvm()
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                compilerOptions {
                    moduleKind = JsModuleKind.MODULE_ES
                    target = "es2015"
                }
                browser { testTask { enabled = false } }
                nodejs { testTask { useMocha { timeout = "60000" } } }
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
            kotlin.srcDir(generatedFailureMessagesSources)
            dependencies {
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCorePublic)
                api(projects.libWalletUnitPublic)
                api(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(libs.bundles.app.platform.di)
            }
        }
        val commonTest by getting {
            kotlin.srcDir(generatedAuthorizationHandoffUriVectorSources)
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}

/**
 * Reads the canonical wallet-interaction-failure-messages.json from the openapi checkout and emits
 * WalletInteractionFailureCatalogue.kt. Keyed on code, never messageKey. Consumers resolve locally;
 * the engine must not populate WalletInteractionError.message from this catalogue.
 */
val generateWalletInteractionFailureCatalogue by tasks.registering {
    group = "build"
    description = "Generates WalletInteractionFailureCatalogue.kt from the canonical JSON."

    val catalogueFile = openapiSpec("wallet-interaction-failure-messages.json")
    val outputDirFile = generatedFailureMessagesSources.get().asFile

    inputs.file(catalogueFile)
    outputs.dir(outputDirFile)

    doLast {
        val dottedIdentifier = Regex("[A-Za-z][A-Za-z0-9_]*\\.[A-Za-z][A-Za-z0-9_.]*")
        val dispositions = listOf("TERMINAL", "REPEATABLE", "RESUMABLE")

        fun asObject(
            value: Any?,
            label: String,
        ): Map<String, Any> {
            require(value is Map<*, *>) { "$label must be an object" }
            return value.entries.associate { (key, nested) ->
                require(key is String && key.isNotBlank()) { "$label keys must be non-empty strings" }
                key to (nested ?: error("$label.$key is null"))
            }
        }

        fun holderCopy(
            label: String,
            text: Any?,
        ): String {
            require(text is String && text.isNotBlank()) { "$label must be a non-empty string" }
            require('\u2014' !in text) { "$label contains an em-dash" }
            require(!text.contains("vouched", ignoreCase = true)) { "$label contains 'vouched'" }
            require(!text.contains("wallet.interaction.")) { "$label contains a messageKey" }
            val dotted = dottedIdentifier.find(text)
            require(dotted == null) { "$label contains a dotted identifier: ${dotted!!.value}" }
            return text
        }

        fun localeMap(
            value: Any?,
            label: String,
        ): Map<String, String> {
            val obj = asObject(value, label)
            require(obj.isNotEmpty()) { "$label must have at least one locale" }
            return obj.mapValues { (locale, text) -> holderCopy("$label.$locale", text) }
        }

        fun escapeKotlin(text: String): String =
            text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\$", "\\\$")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")

        val root = groovy.json.JsonSlurper().parse(catalogueFile) as Map<*, *>
        val defaultLocale = root["defaultLocale"]
        require(defaultLocale is String && defaultLocale.isNotBlank()) { "defaultLocale must be a non-empty string" }
        val byDisposition = asObject(root["byDisposition"], "byDisposition")
        val byCode = asObject(root["byCode"], "byCode")
        require(byDisposition.keys.containsAll(dispositions)) {
            "byDisposition must include ${dispositions.joinToString()}"
        }
        require(byDisposition.keys.all { it in dispositions }) {
            "byDisposition has unknown keys: ${byDisposition.keys - dispositions.toSet()}"
        }

        val dispositionLocales =
            dispositions.associateWith { name ->
                val locales = localeMap(byDisposition.getValue(name), "byDisposition.$name")
                require(defaultLocale in locales) { "byDisposition.$name missing default locale $defaultLocale" }
                locales
            }
        val codeLocales =
            byCode.keys.sorted().associateWith { code ->
                require(code.isNotBlank()) { "byCode contains a blank key" }
                require(!code.startsWith("wallet.interaction.")) { "byCode is keyed on a messageKey: $code" }
                val locales = localeMap(byCode.getValue(code), "byCode.$code")
                require(defaultLocale in locales) { "byCode.$code missing default locale $defaultLocale" }
                locales.values.forEach { text ->
                    require(code !in text) { "byCode.$code copy contains its own code" }
                }
                locales
            }

        val allLocales =
            (
                listOf(defaultLocale) +
                    dispositionLocales.values.flatMap { it.keys } +
                    codeLocales.values.flatMap { it.keys }
            ).toSortedSet()

        fun localeLookup(
            locales: Map<String, String>,
            locale: String,
        ): String = locales[locale] ?: locales.getValue(defaultLocale)

        val sb = StringBuilder()
        sb.appendLine("/*")
        sb.appendLine(" * GENERATED FILE - do not edit by hand.")
        sb.appendLine(" * Regenerate via './gradlew :lib-wallet-interaction-public:generateWalletInteractionFailureCatalogue'.")
        sb.appendLine(" * Source: wallet-interaction-failure-messages.json in the openapi checkout.")
        sb.appendLine(" */")
        sb.appendLine("package com.sphereon.wallet.interaction")
        sb.appendLine()
        sb.appendLine("/**")
        sb.appendLine(" * Holder-facing failure copy, keyed on `code` never `messageKey`.")
        sb.appendLine(" *")
        sb.appendLine(" * Resolve CLIENT side in the device locale. Do not populate")
        sb.appendLine(" * [WalletInteractionError.message] from this catalogue; that field stays the first rung")
        sb.appendLine(" * of [resolveFailureMessage] and is filled only if the engine itself resolved prose.")
        sb.appendLine(" */")
        sb.appendLine("object WalletInteractionFailureCatalogue {")
        sb.appendLine("    const val DEFAULT_LOCALE: String = \"${escapeKotlin(defaultLocale)}\"")
        sb.appendLine()
        sb.appendLine("    private val byDispositionByLocale: Map<String, Map<WalletFailureDisposition, String>> = mapOf(")
        allLocales.forEach { locale ->
            sb.appendLine("        \"${escapeKotlin(locale)}\" to mapOf(")
            dispositions.forEach { name ->
                val text = localeLookup(dispositionLocales.getValue(name), locale)
                sb.appendLine("            WalletFailureDisposition.$name to \"${escapeKotlin(text)}\",")
            }
            sb.appendLine("        ),")
        }
        sb.appendLine("    )")
        sb.appendLine()
        sb.appendLine("    private val byCodeByLocale: Map<String, Map<String, String>> = mapOf(")
        allLocales.forEach { locale ->
            sb.appendLine("        \"${escapeKotlin(locale)}\" to mapOf(")
            codeLocales.forEach { (code, locales) ->
                sb.appendLine("            \"${escapeKotlin(code)}\" to \"${escapeKotlin(localeLookup(locales, locale))}\",")
            }
            sb.appendLine("        ),")
        }
        sb.appendLine("    )")
        sb.appendLine()
        sb.appendLine("    fun byDisposition(locale: String = DEFAULT_LOCALE): Map<WalletFailureDisposition, String> =")
        sb.appendLine("        localize(byDispositionByLocale, locale)")
        sb.appendLine()
        sb.appendLine("    fun byCode(locale: String = DEFAULT_LOCALE): Map<String, String> =")
        sb.appendLine("        localize(byCodeByLocale, locale)")
        sb.appendLine()
        sb.appendLine("    fun messageForCode(code: String, locale: String = DEFAULT_LOCALE): String? =")
        sb.appendLine("        byCode(locale)[code] ?: byCode(DEFAULT_LOCALE)[code]")
        sb.appendLine()
        sb.appendLine("    fun messageForDisposition(disposition: WalletFailureDisposition, locale: String = DEFAULT_LOCALE): String =")
        sb.appendLine("        byDisposition(locale).getValue(disposition)")
        sb.appendLine()
        sb.appendLine("    private fun <V> localize(table: Map<String, V>, locale: String): V {")
        sb.appendLine("        table[locale]?.let { return it }")
        sb.appendLine("        val language = locale.substringBefore('-').substringBefore('_')")
        sb.appendLine("        if (language.isNotEmpty() && language != locale) {")
        sb.appendLine("            table[language]?.let { return it }")
        sb.appendLine("        }")
        sb.appendLine("        return table.getValue(DEFAULT_LOCALE)")
        sb.appendLine("    }")
        sb.appendLine("}")
        sb.appendLine()
        sb.appendLine("fun resolveFailureMessage(")
        sb.appendLine("    failure: WalletInteractionError,")
        sb.appendLine("    locale: String = WalletInteractionFailureCatalogue.DEFAULT_LOCALE,")
        sb.appendLine("): String =")
        sb.appendLine("    resolveFailureMessage(")
        sb.appendLine("        disposition = failure.disposition,")
        sb.appendLine("        message = failure.message,")
        sb.appendLine("        code = failure.code,")
        sb.appendLine("        locale = locale,")
        sb.appendLine("    )")
        sb.appendLine()
        sb.appendLine("fun resolveFailureMessage(")
        sb.appendLine("    disposition: WalletFailureDisposition,")
        sb.appendLine("    message: String? = null,")
        sb.appendLine("    code: String? = null,")
        sb.appendLine("    locale: String = WalletInteractionFailureCatalogue.DEFAULT_LOCALE,")
        sb.appendLine("): String {")
        sb.appendLine("    if (!message.isNullOrEmpty()) return message")
        sb.appendLine("    val specific = code?.let { WalletInteractionFailureCatalogue.messageForCode(it, locale) }")
        sb.appendLine("    if (specific != null) return specific")
        sb.appendLine("    return WalletInteractionFailureCatalogue.messageForDisposition(disposition, locale)")
        sb.appendLine("}")
        sb.appendLine()

        outputDirFile.deleteRecursively()
        val packageDir = outputDirFile.resolve("com/sphereon/wallet/interaction")
        packageDir.mkdirs()
        packageDir.resolve("WalletInteractionFailureCatalogue.kt").writeText(sb.toString(), Charsets.UTF_8)
    }
}

val readsGeneratedFailureMessages: (org.gradle.api.Task) -> Boolean = { task ->
    val name = task.name
    name.startsWith("compileKotlin") ||
        name.startsWith("compileCommonMain") ||
        name.startsWith("runKtlintCheck") ||
        name.startsWith("runKtlintFormat") ||
        name.startsWith("detekt") ||
        name.endsWith("SourcesJar") ||
        name == "sourcesJar"
}
tasks.matching(readsGeneratedFailureMessages).configureEach {
    dependsOn(generateWalletInteractionFailureCatalogue)
}

/**
 * Reads authorization-handoff-uri-vectors.json from the openapi checkout and emits the list the
 * Kotlin allow-list test iterates. Unknown `expected` values fail the generate; the test also
 * fails them at runtime so a stale or hand-edited catalogue cannot skip a vector.
 */
val generateAuthorizationHandoffUriVectors by tasks.registering {
    group = "build"
    description = "Generates AuthorizationHandoffUriVectors.kt from the canonical JSON."

    val vectorsFile = openapiSpec("authorization-handoff-uri-vectors.json")
    val outputDirFile = generatedAuthorizationHandoffUriVectorSources.get().asFile

    inputs.file(vectorsFile)
    outputs.dir(outputDirFile)

    doLast {
        fun escapeKotlin(text: String): String =
            text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\$", "\\\$")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")

        val parsed = groovy.json.JsonSlurper().parse(vectorsFile)
        require(parsed is List<*>) { "authorization-handoff-uri-vectors.json must be an array" }
        require(parsed.isNotEmpty()) { "authorization-handoff-uri-vectors.json must not be empty" }

        val allowedKeys = setOf("uri", "expected", "why")
        val allowedExpected = setOf("admit", "refuse")
        val vectors =
            parsed.mapIndexed { index, entry ->
                require(entry is Map<*, *>) { "vector[$index] must be an object" }
                val keys = entry.keys.map { it.toString() }.toSet()
                val unknown = keys - allowedKeys
                require(unknown.isEmpty()) { "vector[$index] has unknown fields: $unknown" }
                require("uri" in keys) { "vector[$index] is missing uri" }
                require("expected" in keys) { "vector[$index] is missing expected" }
                require("why" in keys) { "vector[$index] is missing why" }
                val uri = entry["uri"]
                val expected = entry["expected"]
                val why = entry["why"]
                require(uri is String) { "vector[$index].uri must be a string" }
                require(expected is String && expected in allowedExpected) {
                    "vector[$index].expected must be admit or refuse, was: $expected"
                }
                require(why is String && why.isNotBlank()) { "vector[$index].why must be a non-empty string" }
                require('\u2014' !in why) { "vector[$index].why contains an em-dash" }
                Triple(uri, expected, why)
            }

        val sb = StringBuilder()
        sb.appendLine("/*")
        sb.appendLine(" * GENERATED FILE - do not edit by hand.")
        sb.appendLine(" * Regenerate via './gradlew :lib-wallet-interaction-public:generateAuthorizationHandoffUriVectors'.")
        sb.appendLine(" * Source: authorization-handoff-uri-vectors.json in the openapi checkout.")
        sb.appendLine(" */")
        sb.appendLine("package com.sphereon.wallet.interaction")
        sb.appendLine()
        sb.appendLine("data class AuthorizationHandoffUriVector(")
        sb.appendLine("    val uri: String,")
        sb.appendLine("    val expected: String,")
        sb.appendLine("    val why: String,")
        sb.appendLine(")")
        sb.appendLine()
        sb.appendLine("object AuthorizationHandoffUriVectors {")
        sb.appendLine("    val all: List<AuthorizationHandoffUriVector> = listOf(")
        vectors.forEach { (uri, expected, why) ->
            sb.appendLine("        AuthorizationHandoffUriVector(")
            sb.appendLine("            uri = \"${escapeKotlin(uri)}\",")
            sb.appendLine("            expected = \"${escapeKotlin(expected)}\",")
            sb.appendLine("            why = \"${escapeKotlin(why)}\",")
            sb.appendLine("        ),")
        }
        sb.appendLine("    )")
        sb.appendLine("}")
        sb.appendLine()

        outputDirFile.deleteRecursively()
        val packageDir = outputDirFile.resolve("com/sphereon/wallet/interaction")
        packageDir.mkdirs()
        packageDir.resolve("AuthorizationHandoffUriVectors.kt").writeText(sb.toString(), Charsets.UTF_8)
    }
}

val readsGeneratedAuthorizationHandoffUriVectors: (org.gradle.api.Task) -> Boolean = { task ->
    val name = task.name
    name.contains("Test") && (
        name.startsWith("compile") ||
            name.startsWith("runKtlintCheck") ||
            name.startsWith("runKtlintFormat") ||
            name.startsWith("detekt")
        )
}
tasks.matching(readsGeneratedAuthorizationHandoffUriVectors).configureEach {
    dependsOn(generateAuthorizationHandoffUriVectors)
}
