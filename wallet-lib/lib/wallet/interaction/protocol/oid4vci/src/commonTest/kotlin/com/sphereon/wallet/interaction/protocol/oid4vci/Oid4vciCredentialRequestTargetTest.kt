/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Oid4vciCredentialRequestTargetTest {
    @Test
    fun credentialIdentifierIsTheWireSelectorForSingleAndBatchProofRequests() {
        val details =
            listOf(
                buildJsonObject {
                    put("type", JsonPrimitive("openid_credential"))
                    put("credential_configuration_id", JsonPrimitive("run-eupid"))
                    put("credential_identifiers", JsonArray(listOf(JsonPrimitive("eupid-dataset-1"), JsonPrimitive("eupid-dataset-2"))))
                },
            )

        for (proofCount in listOf(1, 2)) {
            val target = selectCredentialRequestTarget(details, "run-eupid")
            assertTrue(target.isOk, "A matching authorized credential detail should resolve")
            val wireRequest = wireRequest(target.value.credentialConfigurationId, target.value.credentialIdentifier, proofCount)

            assertEquals("eupid-dataset-1", wireRequest["credential_identifier"]?.let { (it as JsonPrimitive).content })
            assertFalse(wireRequest.containsKey("credential_configuration_id"), "Identifier-mode request must not also send configuration_id")
            val proofs = wireRequest["proofs"] as JsonObject
            assertEquals(proofCount, (proofs["jwt"] as JsonArray).size)
        }
    }

    @Test
    fun absentCredentialAuthorizationDetailsRetainsScopeModeConfigurationSelector() {
        val target = selectCredentialRequestTarget(null, "scope-authorized-eupid")

        assertTrue(target.isOk)
        val wireRequest = wireRequest(target.value.credentialConfigurationId, target.value.credentialIdentifier)
        assertEquals("scope-authorized-eupid", (wireRequest["credential_configuration_id"] as JsonPrimitive).content)
        assertFalse(wireRequest.containsKey("credential_identifier"))
    }

    @Test
    fun ignoresNonCredentialAuthorizationDetailWhenUsingScopeMode() {
        val details = listOf(buildJsonObject { put("type", JsonPrimitive("other_resource")) })
        val target = selectCredentialRequestTarget(details, "scope-authorized-eupid")

        assertTrue(target.isOk)
        assertEquals("scope-authorized-eupid", target.value.credentialConfigurationId)
        assertEquals(null, target.value.credentialIdentifier)
    }

    @Test
    fun rejectsMalformedMissingOrMismatchedCredentialAuthorizationDetails() {
        val malformedCases =
            listOf(
                emptyList(),
                listOf(JsonPrimitive("not-an-object")),
                listOf(buildJsonObject { put("credential_configuration_id", JsonPrimitive("run-eupid")) }),
                listOf(credentialDetail(configurationId = "run-eupid", identifiers = null)),
                listOf(credentialDetail(configurationId = "run-eupid", identifiers = emptyList())),
                listOf(credentialDetail(configurationId = "run-eupid", identifiers = listOf(""))),
                listOf(credentialDetail(configurationId = "run-eupid", identifiers = listOf("same", "same"))),
                listOf(credentialDetail(configurationId = "other-config", identifiers = listOf("other-dataset"))),
                listOf(
                    credentialDetail(configurationId = "run-eupid", identifiers = listOf("eupid-dataset")),
                    credentialDetail(configurationId = "run-eupid", identifiers = listOf("duplicate-detail-dataset")),
                ),
            )

        for (details in malformedCases) {
            val target = selectCredentialRequestTarget(details, "run-eupid")
            assertTrue(target.isErr, "Malformed, missing, ambiguous, or mismatched authorization detail must fail closed")
        }
    }

    @Test
    fun rejectsCredentialAuthorizationDetailsWithoutRequestedConfiguration() {
        val details = listOf(credentialDetail(configurationId = "run-eupid", identifiers = listOf("eupid-dataset")))
        assertTrue(selectCredentialRequestTarget(details, null).isErr)
    }

    private fun credentialDetail(configurationId: String, identifiers: List<String>?): JsonObject =
        buildJsonObject {
            put("type", JsonPrimitive("openid_credential"))
            put("credential_configuration_id", JsonPrimitive(configurationId))
            if (identifiers != null) put("credential_identifiers", JsonArray(identifiers.map { JsonPrimitive(it) }))
        }

    private fun wireRequest(configurationId: String?, credentialIdentifier: String?, proofCount: Int = 0): JsonObject =
        Json {
            explicitNulls = false
            encodeDefaults = false
        }.encodeToString(
            CredentialRequest.serializer(),
            CredentialRequest(
                credentialConfigurationId = configurationId,
                credentialIdentifier = credentialIdentifier,
                proofs = if (proofCount == 0) null else CredentialRequestProofs("jwt", List(proofCount) { JsonPrimitive("proof-$it") }),
            ),
        ).let { Json.parseToJsonElement(it).jsonObject }
}
