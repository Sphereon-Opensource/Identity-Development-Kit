/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

// Shared plan and provisioning model for deployed-product conformance drivers.

import com.sphereon.oidf.conformance.OidfPlanScenario
import com.sphereon.oidf.conformance.OidfPlanScenarioManifest
import com.sphereon.oidf.conformance.OidfRole
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun OidfProductStack.planConfig(
    scenario: OidfPlanScenario,
    walletClientPublicJwk: JsonObject? = null,
): String {
    val overrides =
        when (scenario.requiredPlan.role) {
            OidfRole.ISSUER -> issuerPlanOverrides(scenario)
            OidfRole.VERIFIER -> verifierPlanOverrides(scenario)
            OidfRole.WALLET -> walletPlanOverrides(scenario, walletClientPublicJwk)
        }
    return configPreprocessor.config(
        relativePath = scenario.configTemplate,
        overrides =
            mergeJsonObjects(
                scenario.configOverrides,
                mergeJsonObjects(
                    buildJsonObject { put("alias", oidfSuiteAlias(scenario)) },
                    overrides,
                ),
            ),
    )
}

private fun OidfProductStack.walletPlanOverrides(
    scenario: OidfPlanScenario,
    walletClientPublicJwk: JsonObject?,
): JsonObject =
    buildJsonObject {
        walletClientPublicJwk?.let { publicJwk ->
            require("d" !in publicJwk) { "Wallet client registration JWK must be public" }
            put(
                "client",
                buildJsonObject {
                    put("jwks", buildJsonObject { put("keys", buildJsonArray { add(publicJwk) }) })
                },
            )
        }
        if (scenario.planName.contains("haip")) {
            put(
                "client_attestation",
                buildJsonObject {
                    put("trust_anchor", productCaPem)
                    put("key_attestation_trust_anchor_pem", productCaPem)
                },
            )
        }
    }

internal data class OidfIssuerScenarioClients(
    val scenarioId: String,
    val clientId: String,
    val client2Id: String,
    val redirectUri: String,
)

internal fun oidfIssuerScenarioClients(
    advertisedSuiteBaseUrl: String,
    profile: OidfProductProfile,
): List<OidfIssuerScenarioClients> =
    OidfPlanScenarioManifest.scenarios
        .filter {
            it.requiredPlan.role == OidfRole.ISSUER &&
                (it.planName.contains("haip") == (profile == OidfProductProfile.HAIP))
        }
        .map { scenario ->
            OidfIssuerScenarioClients(
                scenarioId = scenario.id,
                clientId = oidfIssuerClientId(scenario, 1),
                client2Id = oidfIssuerClientId(scenario, 2),
                redirectUri = "$advertisedSuiteBaseUrl/test/a/${oidfSuiteAlias(scenario)}/callback",
            )
        }.sortedBy { it.scenarioId }

private fun oidfSuiteAlias(scenario: OidfPlanScenario): String = "vdx-${scenario.id}"

private fun oidfIssuerClientId(
    scenario: OidfPlanScenario,
    ordinal: Int,
): String = "oidf-wallet-client-$ordinal-${scenario.id}"

private fun OidfProductStack.issuerPlanOverrides(scenario: OidfPlanScenario): JsonObject {
    val credentialConfigurationId =
        when (scenario.variant.getValue("credential_format")) {
            "sd_jwt_vc" -> "EuPid"
            "mdoc" -> "Mdl"
            else -> error("Unsupported issuer credential format in ${scenario.id}: ${scenario.variant["credential_format"]}")
        }
    val suiteSigningJwk = configPreprocessor.sourceJson("scripts/certs-keys/vp-signing-jwk.json")
    val client2SigningJwk = JsonObject(suiteSigningJwk + ("kid" to JsonPrimitive("vci-example-key-2")))
    val suiteSigningCa = configPreprocessor.sourceText("scripts/certs-keys/vp-signing-ca.crt")
    val suiteSigningJwks = buildJsonObject { put("keys", buildJsonArray { add(suiteSigningJwk) }) }
    val client2SigningJwks = buildJsonObject { put("keys", buildJsonArray { add(client2SigningJwk) }) }
    return buildJsonObject {
        put("server", buildJsonObject { put("discoveryIssuer", publicBaseUrl) })
        put(
            "client",
            buildJsonObject {
                put("client_id", oidfIssuerClientId(scenario, 1))
            },
        )
        put(
            "client2",
            buildJsonObject {
                put("client_id", oidfIssuerClientId(scenario, 2))
                // The upstream no-mTLS fixture uses an RSA client-authentication key here,
                // while VCIGenerateJwtProof requires an ES256/P-256 credential-binding key.
                // Use the suite's EC fixture with a distinct key id so the multiple-client
                // module exercises VDX instead of stopping during suite configuration.
                put("jwks", client2SigningJwks)
            },
        )
        put(
            "vci",
            buildJsonObject {
                put("credential_issuer_url", publicBaseUrl)
                put("authorization_server", publicBaseUrl)
                put("credential_configuration_id", credentialConfigurationId)
            },
        )
        put(
            "client_attestation",
            buildJsonObject {
                put("issuer", "https://client-attester.example.org/")
                put("trust_anchor", suiteSigningCa)
                put("attester_jwks", suiteSigningJwks)
                put("key_attestation_trust_anchor_pem", suiteSigningCa)
                put("key_attestation_jwks", suiteSigningJwks)
            },
        )
        put(
            "credential",
            buildJsonObject {
                put("trust_anchor_pem", productCaPem)
                put("status_list_trust_anchor_pem", productCaPem)
            },
        )
    }
}

private fun OidfProductStack.verifierPlanOverrides(scenario: OidfPlanScenario): JsonObject {
    val suiteSigningJwk = configPreprocessor.sourceJson("scripts/certs-keys/vp-signing-jwk.json")
    return buildJsonObject {
        put(
            "client",
            buildJsonObject {
                if (scenario.variant["client_id_prefix"] == "x509_san_dns") {
                    put("client_id", verifierSanDns)
                }
                put("request_object_trust_anchor_pem", productCaPem)
            },
        )
        put(
            "credential",
            buildJsonObject {
                put("signing_jwk", suiteSigningJwk)
            },
        )
    }
}

private fun mergeJsonObjects(
    first: JsonObject,
    second: JsonObject,
): JsonObject =
    JsonObject(
        first.toMutableMap().apply {
            second.forEach { (key, value) ->
                val current = this[key]
                this[key] =
                    if (current is JsonObject && value is JsonObject) {
                        mergeJsonObjects(current, value)
                    } else {
                        value
                    }
            }
        },
    )
