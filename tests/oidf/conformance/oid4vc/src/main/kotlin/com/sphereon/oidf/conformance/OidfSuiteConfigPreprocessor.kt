/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/** Expands the pinned suite's checked-in config templates for API-only runners. */
class OidfSuiteConfigPreprocessor(
    val suiteDir: Path,
    private val baseUrl: String,
    private val localBaseUrl: String = baseUrl,
    private val mtlsBaseUrl: String = baseUrl.replace(":8443", ":8444"),
) {
    private val json = Json { prettyPrint = false }

    fun config(
        relativePath: String,
        overrides: JsonObject = JsonObject(emptyMap()),
        removeBrowser: Boolean = true,
    ): String {
        var source = suiteDir.resolve(relativePath).readText()
        urlReplacements().forEach { (placeholder, value) -> source = source.replace("{$placeholder}", value) }
        source = expandFilePlaceholders(source)

        val parsed = json.parseToJsonElement(source).jsonObject
        val headless = if (removeBrowser) JsonObject(parsed.filterKeys { it != "browser" }) else parsed
        val expandedOverrides =
            json.parseToJsonElement(expandFilePlaceholders(overrides.toString())).jsonObject
        return deepMerge(headless, expandedOverrides).toString()
    }

    fun walletConfig(relativePath: String): String = config(relativePath)

    fun sourceText(relativePath: String): String = resolveSource(relativePath).readText()

    fun sourceJson(relativePath: String): JsonObject = json.parseToJsonElement(sourceText(relativePath)).jsonObject

    private fun urlReplacements(): Map<String, String> =
        mapOf(
            "BASEURL" to baseUrl.ensureTrailingSlash(),
            "EXTERNALBASEURL" to baseUrl.ensureTrailingSlash(),
            "LOCALBASEURL" to localBaseUrl.ensureTrailingSlash(),
            "BASEURLMTLS" to mtlsBaseUrl.ensureTrailingSlash(),
            "HOSTNAME" to URI.create(baseUrl).host,
        )

    private fun expandFilePlaceholders(source: String): String =
        FILE_PLACEHOLDER.replace(source) { match ->
            val fileName = match.groupValues[1]
            val contents = suiteDir.resolve("scripts/certs-keys").resolve(fileName).readText()
            if (fileName.endsWith(".json")) {
                contents
            } else {
                // PEM placeholders occur inside a JSON string in the upstream templates.
                JsonPrimitive(contents).toString().removeSurrounding("\"")
            }
        }

    private fun resolveSource(relativePath: String): Path {
        require(!Path.of(relativePath).isAbsolute) { "OIDF suite source path must be relative: $relativePath" }
        val normalizedSuiteDir = suiteDir.toAbsolutePath().normalize()
        val source = normalizedSuiteDir.resolve(relativePath).normalize()
        require(source.startsWith(normalizedSuiteDir)) { "OIDF suite source path escapes the locked checkout: $relativePath" }
        require(Files.isRegularFile(source)) { "Missing locked OIDF suite source file: $relativePath" }
        return source
    }

    private fun deepMerge(
        base: JsonObject,
        overrides: JsonObject,
    ): JsonObject =
        JsonObject(
            base.toMutableMap().apply {
                overrides.forEach { (key, override) ->
                    val current = this[key]
                    this[key] =
                        if (current is JsonObject && override is JsonObject) {
                            deepMerge(current, override)
                        } else {
                            override
                        }
                }
            },
        )

    companion object {
        const val VCI_WALLET_CLIENT_AUTH_CONFIG: String =
            "scripts/test-configs-rp-against-op/vci-issuer-test-config.json"

        private val FILE_PLACEHOLDER = Regex("\\{([A-Za-z0-9._/-]+\\.(?:json|crt|key|pem))}")

        val requiredSourceFiles: Set<String> =
            OidfPlanScenarioManifest.scenarios.mapTo(linkedSetOf()) { it.configTemplate } +
                setOf(
                    VCI_WALLET_CLIENT_AUTH_CONFIG,
                    "scripts/certs-keys/vp-server-jwk.json",
                    "scripts/certs-keys/vp-signing-jwk.json",
                    "scripts/certs-keys/vp-signing-ca.crt",
                )
    }
}

private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
