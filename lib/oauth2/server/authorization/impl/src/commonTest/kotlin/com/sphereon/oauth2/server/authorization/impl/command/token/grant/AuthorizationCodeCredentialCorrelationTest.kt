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
import kotlin.test.assertFalse

class AuthorizationCodeCredentialCorrelationTest {
    @Test
    fun offerLinkedAuthorizationCodeCarriesExactOfferSessionIdentifier() {
        val detail =
            buildAuthorizationCodeCredentialAuthorizationDetails(
                credentialConfigurationIds = listOf("shared-config"),
                issuerState = "offer-session-exact",
            )!![0].jsonObject

        assertEquals("shared-config", detail["credential_configuration_id"]?.jsonPrimitive?.content)
        assertEquals(listOf("offer-session-exact"), detail["credential_identifiers"]?.jsonArray?.map { it.jsonPrimitive.content })
    }

    @Test
    fun walletInitiatedAuthorizationCodeKeepsConfigurationFlowWithoutInventingIdentifier() {
        val detail =
            buildAuthorizationCodeCredentialAuthorizationDetails(
                credentialConfigurationIds = listOf("shared-config"),
                issuerState = null,
            )!![0].jsonObject

        assertEquals("shared-config", detail["credential_configuration_id"]?.jsonPrimitive?.content)
        assertFalse("credential_identifiers" in detail)
    }
}
