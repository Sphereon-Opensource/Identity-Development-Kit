/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.command.token.grant

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthorizationCodeCredentialCorrelationTest {
    @Test
    fun tokenResponseReturnsCredentialIdentifierDistinctFromConfigurationId() {
        val detail =
            buildAuthorizationCodeCredentialAuthorizationDetails(
                credentialConfigurationIds = listOf("shared-config"),
                credentialIdentifierProvider = { "dataset-handle-001" },
            )!![0].jsonObject

        assertEquals("shared-config", detail["credential_configuration_id"]?.jsonPrimitive?.content)
        val identifiers = detail["credential_identifiers"]?.jsonArray?.map { it.jsonPrimitive.content }
        assertEquals(listOf("dataset-handle-001"), identifiers)
        assertNotEquals(detail["credential_configuration_id"]?.jsonPrimitive?.content, identifiers?.single())
    }

    @Test
    fun eachAuthorizedConfigurationGetsItsOwnIdentifier() {
        val details =
            buildAuthorizationCodeCredentialAuthorizationDetails(
                credentialConfigurationIds = listOf("pid", "mdl"),
                credentialIdentifierProvider = { configId -> "dataset-$configId" },
            )!!.map { it.jsonObject }

        assertEquals(listOf("pid", "mdl"), details.map { it["credential_configuration_id"]?.jsonPrimitive?.content })
        assertEquals(
            listOf(listOf("dataset-pid"), listOf("dataset-mdl")),
            details.map { detail -> detail["credential_identifiers"]?.jsonArray?.map { it.jsonPrimitive.content } },
        )
    }

    @Test
    fun opaqueTokenCredentialIdentifierCarriesOfferSessionCorrelation() {
        val sessionId = "ff6b2102-c658-4c96-9d6e-41ad809d5b0f"
        val detail =
            buildAuthorizationCodeCredentialAuthorizationDetails(
                credentialConfigurationIds = listOf("employee-card"),
                issuanceSessionId = sessionId,
            )!![0].jsonObject

        val identifier = detail["credential_identifiers"]!!.jsonArray.single().jsonPrimitive.content
        assertTrue(identifier.startsWith("urn:vdx:oid4vci:credential:$sessionId:"))
        assertNotEquals("employee-card", identifier)
    }

    @Test
    fun refreshTokenResponsesRemintCredentialIdentifiers() {
        fun identifier() =
            buildRefreshedCredentialAuthorizationDetails(listOf("mdl"))!![0]
                .jsonObject["credential_identifiers"]!!
                .jsonArray.single().jsonPrimitive.content

        val firstAccessTokenIdentifier = identifier()
        val refreshedAccessTokenIdentifier = identifier()

        assertNotEquals(firstAccessTokenIdentifier, refreshedAccessTokenIdentifier)
    }
}
