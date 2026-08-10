/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

import com.sphereon.oidf.conformance.OidfPlan
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

@Serializable
private data class OidfIssuerInitiationEvidence(
    val testId: String,
    val credentialConfigurationId: String,
    val grantType: String,
    val offerDelivered: Boolean,
    val transactionCodeDelivered: Boolean,
)

/** Drives the issuer side of the suite's issuer-initiated OID4VCI test modules. */
internal class OidfIssuerInitiationDriver(
    private val stack: OidfProductStack,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val productClient =
        HttpClient
            .newBuilder()
            .sslContext(stack.productSslContext)
            .connectTimeout(Duration.ofSeconds(5))
            .build()

    fun deliverIfRequested(
        testId: String,
        plan: OidfPlan,
        moduleGrantType: String?,
        evidenceDirectory: Path,
        round: Int,
    ): Boolean {
        require(round > 0) { "Issuer-initiation round must be positive" }
        val credentialOfferEndpoint = stack.suite.exposed(testId)[CREDENTIAL_OFFER_ENDPOINT] ?: return false
        val credentialConfigurationId =
            stack.suite
                .infoJson(testId)
                .getValue("config")
                .jsonObject
                .getValue("vci")
                .jsonObject
                .getValue("credential_configuration_id")
                .jsonPrimitive
                .content
        // Final plans select the grant at plan level. HAIP selects only format/flow at plan
        // level and expands authorization_code/pre_authorization_code in each module variant.
        // Use the module's effective value first so issuer-initiated HAIP modules receive the
        // offer requested by the suite instead of waiting forever for a plan-level field.
        val grantType =
            checkNotNull(moduleGrantType ?: plan.selectionVariant["vci_grant_type"]) {
                "OIDF issuer plan ${plan.id} and active module did not expose vci_grant_type"
            }
        val requestBody =
            oidfIssuerCreateOfferBody(
                issuerId = stack.publicBaseUrl,
                testId = testId,
                credentialConfigurationId = credentialConfigurationId,
                grantType = grantType,
            )
        val response =
            productClient.send(
                HttpRequest
                    .newBuilder(
                        URI.create(
                            "${stack.publicBaseUrl}/api/oid4vci/v1/testing/instances/" +
                                "${pathSegment(stack.issuerInstanceId)}/actions/create-offer",
                        ),
                    ).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        require(response.statusCode() == 201) {
            "VDX issuer create-offer returned ${response.statusCode()}: ${response.body()}"
        }
        val created = json.parseToJsonElement(response.body()).jsonObject
        val offerUri = created.getValue("offerUri").jsonPrimitive.content
        val offerQuery = URI.create(offerUri).rawQuery
            ?: error("VDX issuer offer URI did not contain credential-offer query parameters: $offerUri")
        stack.suite.visit("${credentialOfferEndpoint.substringBefore('?')}?$offerQuery")

        val txCode = created["txCode"]?.jsonPrimitive?.content
        var txCodeDelivered = false
        if (!txCode.isNullOrBlank()) {
            val txCodeEndpoint = awaitExposed(testId, TX_CODE_ENDPOINT)
            stack.suite.visit("${txCodeEndpoint.substringBefore('?')}?code=$txCode")
            txCodeDelivered = true
        }

        Files.writeString(
            evidenceDirectory.resolve("issuer-initiation-$round.json"),
            json.encodeToString(
                OidfIssuerInitiationEvidence.serializer(),
                OidfIssuerInitiationEvidence(
                    testId = testId,
                    credentialConfigurationId = credentialConfigurationId,
                    grantType = grantType,
                    offerDelivered = true,
                    transactionCodeDelivered = txCodeDelivered,
                ),
            ),
        )
        return true
    }

    private fun awaitExposed(
        testId: String,
        name: String,
    ): String {
        repeat(60) {
            stack.suite.exposed(testId)[name]?.let { return it }
            Thread.sleep(250)
        }
        error("OIDF issuer module $testId did not expose $name after receiving the credential offer")
    }

    private companion object {
        const val CREDENTIAL_OFFER_ENDPOINT = "credential_offer_endpoint"
        const val TX_CODE_ENDPOINT = "tx_code_endpoint"

        fun pathSegment(value: String): String =
            URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20")
    }
}

/** Maps an OIDF issuer-initiation scenario onto the production testing-console REST contract. */
internal fun oidfIssuerCreateOfferBody(
    issuerId: String,
    testId: String,
    credentialConfigurationId: String,
    grantType: String,
): JsonObject {
    val authorizationCodeGrant = grantType == "authorization_code"
    val preAuthorizedCodeGrant = grantType == "pre_authorization_code"
    require(authorizationCodeGrant || preAuthorizedCodeGrant) {
        "Unsupported OIDF issuer grant type: $grantType"
    }
    val credentialSubjectData = oidfIssuerCredentialSubjectData(credentialConfigurationId)
    return buildJsonObject {
        put("issuerId", issuerId)
        put(
            "credentialConfigurationIds",
            buildJsonArray { add(JsonPrimitive(credentialConfigurationId)) },
        )
        put("preAuthorizedCodeGrant", preAuthorizedCodeGrant)
        put("authorizationCodeGrant", authorizationCodeGrant)
        put("txCodeRequired", preAuthorizedCodeGrant)
        if (authorizationCodeGrant) {
            put("state", "oidf-$testId")
        }
        put(
            "preSeededGroups",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("contributorId", "oidf-conformance")
                        put("phase", "session_init")
                        put(
                            "attributes",
                            buildJsonArray {
                                credentialSubjectData.forEach { (claim, value) ->
                                    add(
                                        buildJsonObject {
                                            put("path", "/${claim.toJsonPointerToken()}")
                                            put("value", value)
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
    }
}

private fun String.toJsonPointerToken(): String = replace("~", "~0").replace("/", "~1")
