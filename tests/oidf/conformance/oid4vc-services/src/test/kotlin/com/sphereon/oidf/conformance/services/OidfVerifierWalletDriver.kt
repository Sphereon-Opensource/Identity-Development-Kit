/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

import com.sphereon.oidf.conformance.OidfPlanScenario
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * API-only bridge between the deployable verifier and the OIDF mock wallet.
 *
 * The verifier deliberately emits the standard `openid4vp://` deeplink. In a certification run the
 * suite replaces the human wallet: this driver preserves the verifier-produced query byte-for-byte
 * and sends it to the suite's exposed HTTPS authorization endpoint. No TLS or hostname checks are
 * disabled.
 */
internal class OidfVerifierWalletDriver(
    private val stack: OidfProductStack,
    private val evidenceDirectory: Path,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val productClient =
        HttpClient
            .newBuilder()
            .sslContext(stack.productSslContext)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    private val suiteClient =
        HttpClient
            .newBuilder()
            .sslContext(stack.productSslContext)
            .connectTimeout(Duration.ofSeconds(10))
            // The suite redirects the user agent to the verifier-provided
            // direct_post_response_redirect_uri after it has completed the protocol exchange.
            // That browser landing page is not the protocol result endpoint and may require its
            // own application authentication. Preserve the suite's terminal 3xx response, then
            // read the authoritative result from the production testing-console status API below.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    private val logPath = evidenceDirectory.resolve("verifier-wallet-driver.log")

    fun complete(
        testId: String,
        scenario: OidfPlanScenario,
        moduleName: String,
    ): JsonObject {
        Files.createDirectories(evidenceDirectory)
        val authorizationEndpoint =
            stack.suite
                .runner(testId)["exposed"]
                ?.jsonObject
                ?.get("authorization_endpoint")
                ?.jsonPrimitive
                ?.content
                ?: error("OIDF test $testId did not expose authorization_endpoint")
        val createBody =
            buildJsonObject {
                val requestMethod = scenario.variant["request_method"] ?: "request_uri_signed"
                put("dcql_query", dcqlQuery(scenario.variant.getValue("credential_format")))
                put("client_id_scheme", verifierClientIdScheme(scenario))
                put(
                    "authorization_request_method",
                    if (requestMethod == "url_query") "url_query" else "request_uri",
                )
                if (requestMethod != "url_query") {
                    put("request_uri_base", stack.publicBaseUrl)
                }
                put("response_uri", "${stack.publicBaseUrl}/oid4vp/auth/response")
                if (requestMethod != "url_query") {
                    put(
                        "request_uri_method",
                        if (stack.spec.profile == OidfProductProfile.HAIP || moduleName == REQUEST_URI_METHOD_POST_MODULE) "post" else "get",
                    )
                }
                if (scenario.variant["client_id_prefix"] == "redirect_uri") {
                    put("client_id", "redirect_uri:${stack.publicBaseUrl}/oid4vp/auth/response")
                }
                put("response_mode", scenario.variant.getValue("response_mode"))
                if (stack.spec.profile == OidfProductProfile.HAIP) {
                    put("wallet_uri_scheme", "haip-vp")
                    put("direct_post_response_redirect_uri", "${stack.publicBaseUrl}/oid4vp/conformance/complete")
                }
            }.toString()
        val createResponse =
            send(
                client = productClient,
                request =
                    HttpRequest
                        .newBuilder(
                            URI.create(
                                "${stack.publicBaseUrl}/api/oid4vp/v1/testing/instances/" +
                                    "${pathSegment(stack.verifierInstanceId)}/actions/create-auth-request",
                            ),
                        )
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(createBody))
                        .build(),
                label = "create verifier request",
            )
        require(createResponse.statusCode() == 201) {
            "Verifier request creation returned ${createResponse.statusCode()}: ${createResponse.body()}"
        }
        val created = json.parseToJsonElement(createResponse.body()).jsonObject
        val deeplink = created.getValue("request_uri").jsonPrimitive.content
        val query = deeplink.substringAfter('?', missingDelimiterValue = "")
        require(query.isNotBlank()) { "Verifier returned a deeplink without query parameters: $deeplink" }

        val walletResponse =
            send(
                client = suiteClient,
                request =
                    HttpRequest
                        .newBuilder(URI.create("$authorizationEndpoint?$query"))
                        .timeout(Duration.ofSeconds(60))
                        .GET()
                        .build(),
                label = "invoke OIDF mock wallet",
            )
        require(walletResponse.statusCode() in 200..399) {
            "OIDF mock wallet returned ${walletResponse.statusCode()}: ${walletResponse.body()}"
        }

        val statusUri = created.getValue("status_uri").jsonPrimitive.content
        val resolvedStatusUri = URI.create(stack.publicBaseUrl).resolve(statusUri)
        val statusResponse =
            send(
                client = productClient,
                request = HttpRequest.newBuilder(resolvedStatusUri).timeout(Duration.ofSeconds(30)).GET().build(),
                label = "read verifier result",
            )
        require(statusResponse.statusCode() == 200) {
            "Verifier status returned ${statusResponse.statusCode()}: ${statusResponse.body()}"
        }
        return json.parseToJsonElement(statusResponse.body()).jsonObject
    }

    private fun dcqlQuery(credentialFormat: String): JsonObject =
        buildJsonObject {
            put(
                "credentials",
                buildJsonArray {
                    add(
                        when (credentialFormat) {
                            "sd_jwt_vc" ->
                                buildJsonObject {
                                    put("id", "pid_credential")
                                    put("format", "dc+sd-jwt")
                                    put(
                                        "meta",
                                        buildJsonObject {
                                            put("vct_values", buildJsonArray { add("urn:eudi:pid:1") })
                                        },
                                    )
                                    put(
                                        "claims",
                                        buildJsonArray {
                                            add(buildJsonObject { put("path", buildJsonArray { add("given_name") }) })
                                            add(buildJsonObject { put("path", buildJsonArray { add("family_name") }) })
                                        },
                                    )
                                }

                            "iso_mdl" ->
                                buildJsonObject {
                                    put("id", "my_credential")
                                    put("format", "mso_mdoc")
                                    put("meta", buildJsonObject { put("doctype_value", MDL_DOCTYPE) })
                                    put(
                                        "claims",
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("path", buildJsonArray { add(MDL_NAMESPACE); add("family_name") })
                                                },
                                            )
                                            add(
                                                buildJsonObject {
                                                    put("path", buildJsonArray { add(MDL_NAMESPACE); add("given_name") })
                                                },
                                            )
                                        },
                                    )
                                }

                            else -> error("Unsupported verifier credential format: $credentialFormat")
                        },
                    )
                },
            )
        }

    private fun verifierClientIdScheme(scenario: OidfPlanScenario): String =
        when (scenario.variant["client_id_prefix"] ?: "x509_hash") {
            "x509_hash" -> "X509_HASH"
            "x509_san_dns" -> "X509_SAN_DNS"
            "redirect_uri" -> "REDIRECT_URI"
            else -> error("Unsupported verifier client_id_prefix in ${scenario.id}: ${scenario.variant["client_id_prefix"]}")
        }

    private fun send(
        client: HttpClient,
        request: HttpRequest,
        label: String,
    ): HttpResponse<String> {
        appendLog("$label request ${request.method()} ${request.uri()}")
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        appendLog("$label response ${response.statusCode()} ${response.body().take(4_000)}")
        return response
    }

    private fun appendLog(line: String) {
        Files.writeString(
            logPath,
            "$line\n",
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND,
        )
    }

    private companion object {
        const val REQUEST_URI_METHOD_POST_MODULE = "oid4vp-1final-verifier-request-uri-method-post"
        const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        const val MDL_NAMESPACE = "org.iso.18013.5.1"

        fun pathSegment(value: String): String =
            URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20")
    }
}
