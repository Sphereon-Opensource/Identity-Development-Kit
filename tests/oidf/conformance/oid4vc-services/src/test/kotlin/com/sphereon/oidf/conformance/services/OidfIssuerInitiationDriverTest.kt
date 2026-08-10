/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OidfIssuerInitiationDriverTest {
    @Test
    fun authorizationCodeScenarioUsesTypedTestingConsoleContract() {
        val body =
            oidfIssuerCreateOfferBody(
                issuerId = "https://issuer.example.test",
                testId = "test-123",
                credentialConfigurationId = "EuPid",
                grantType = "authorization_code",
            )

        assertEquals("https://issuer.example.test", body.getValue("issuerId").jsonPrimitive.content)
        assertEquals("EuPid", body.getValue("credentialConfigurationIds").jsonArray.single().jsonPrimitive.content)
        assertTrue(body.getValue("authorizationCodeGrant").jsonPrimitive.content.toBoolean())
        assertFalse(body.getValue("preAuthorizedCodeGrant").jsonPrimitive.content.toBoolean())
        assertFalse(body.getValue("txCodeRequired").jsonPrimitive.content.toBoolean())
        assertEquals("oidf-test-123", body.getValue("state").jsonPrimitive.content)
        assertEquals(
            setOf("/given_name", "/family_name", "/birth_date", "/age_over_18"),
            body.getValue("preSeededGroups").jsonArray
                .single()
                .jsonObject
                .getValue("attributes")
                .jsonArray
                .map { it.jsonObject.getValue("path").jsonPrimitive.content }
                .toSet(),
        )
        assertNull(body["credential_configuration_ids"])
        assertNull(body["credential_subject_data"])
        assertNull(body["grants"])
    }

    @Test
    fun preAuthorizedCodeScenarioRequestsTransactionCode() {
        val body =
            oidfIssuerCreateOfferBody(
                issuerId = "https://issuer.example.test",
                testId = "test-456",
                credentialConfigurationId = "Mdl",
                grantType = "pre_authorization_code",
            )

        assertTrue(body.getValue("preAuthorizedCodeGrant").jsonPrimitive.content.toBoolean())
        assertFalse(body.getValue("authorizationCodeGrant").jsonPrimitive.content.toBoolean())
        assertTrue(body.getValue("txCodeRequired").jsonPrimitive.content.toBoolean())
        assertNull(body["state"])
    }
}
