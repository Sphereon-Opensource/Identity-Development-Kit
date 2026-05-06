/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.server.authorization.impl.command

import com.sphereon.core.api.encodeToBase64
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [extractClientAuthentication] — OIDC Core §9 / RFC 6749 §2.3 ambiguity rejection
 * plus happy-path variant recognition.
 */
class ClientAuthenticationExtractorTest {
    private val jwtAssertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
    private val dummyAssertion = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjbGllbnQxIn0.sig"

    private fun basicHeader(
        user: String,
        pass: String,
    ): Map<String, String> = mapOf("Authorization" to "Basic " + "$user:$pass".encodeToByteArray().encodeToBase64())

    // ========================================================================
    // multi-method rejection
    // ========================================================================

    @Test
    fun clientAuth_basicPlusPost_rejects() {
        val body =
            mapOf(
                "client_id" to listOf("client1"),
                "client_secret" to listOf("body-secret"),
            )
        val result = extractClientAuthentication(body, basicHeader("client1", "header-secret"))
        assertTrue(result.isErr, "Basic header + body client_secret must be rejected as invalid_request")
        val err = result.error
        assertTrue(err is AuthorizationServerError.InvalidRequest)
        assertEquals("invalid_request", err.code)
    }

    @Test
    fun clientAuth_basicPlusJwtAssertion_rejects() {
        val body =
            mapOf(
                "client_assertion_type" to listOf(jwtAssertionType),
                "client_assertion" to listOf(dummyAssertion),
            )
        val result = extractClientAuthentication(body, basicHeader("client1", "header-secret"))
        assertTrue(result.isErr)
        assertTrue(result.error is AuthorizationServerError.InvalidRequest)
    }

    @Test
    fun clientAuth_postPlusJwtAssertion_rejects() {
        val body =
            mapOf(
                "client_id" to listOf("client1"),
                "client_secret" to listOf("body-secret"),
                "client_assertion_type" to listOf(jwtAssertionType),
                "client_assertion" to listOf(dummyAssertion),
            )
        val result = extractClientAuthentication(body, emptyMap())
        assertTrue(result.isErr)
        assertTrue(result.error is AuthorizationServerError.InvalidRequest)
    }

    @Test
    fun clientAuth_attestationPlusBasic_rejects() {
        val body = mapOf("client_id" to listOf("client1"))
        val headers =
            basicHeader("client1", "secret") +
                mapOf(
                    "OAuth-Client-Attestation" to "att.jwt",
                    "OAuth-Client-Attestation-PoP" to "pop.jwt",
                )
        val result = extractClientAuthentication(body, headers)
        assertTrue(result.isErr)
        assertTrue(result.error is AuthorizationServerError.InvalidRequest)
    }

    @Test
    fun clientAuth_attestationPlusJwtAssertion_rejects() {
        val body =
            mapOf(
                "client_assertion_type" to listOf(jwtAssertionType),
                "client_assertion" to listOf(dummyAssertion),
            )
        val headers =
            mapOf(
                "OAuth-Client-Attestation" to "att.jwt",
                "OAuth-Client-Attestation-PoP" to "pop.jwt",
            )
        val result = extractClientAuthentication(body, headers)
        assertTrue(result.isErr)
        assertTrue(result.error is AuthorizationServerError.InvalidRequest)
    }

    // ========================================================================
    // Happy paths — single method accepted
    // ========================================================================

    @Test
    fun clientAuth_basicAlone_accepts() {
        val result = extractClientAuthentication(emptyMap(), basicHeader("client1", "secret"))
        assertTrue(result.isOk)
        val extracted = result.value
        assertTrue(extracted.clientAuthentication is ClientAuthenticationConfig.Basic)
        assertEquals("client1", extracted.clientId)
    }

    @Test
    fun clientAuth_postAlone_accepts() {
        val body =
            mapOf(
                "client_id" to listOf("client1"),
                "client_secret" to listOf("body-secret"),
            )
        val result = extractClientAuthentication(body, emptyMap())
        assertTrue(result.isOk)
        assertTrue(result.value.clientAuthentication is ClientAuthenticationConfig.Post)
    }

    @Test
    fun clientAuth_privateKeyJwtAlone_accepts() {
        val body =
            mapOf(
                "client_id" to listOf("client1"),
                "client_assertion_type" to listOf(jwtAssertionType),
                "client_assertion" to listOf(dummyAssertion),
            )
        val result = extractClientAuthentication(body, emptyMap())
        assertTrue(result.isOk)
        assertTrue(result.value.clientAuthentication is ClientAuthenticationConfig.PrivateKeyJwt)
    }

    @Test
    fun clientAuth_clientIdOnly_accepts_asNone() {
        val body = mapOf("client_id" to listOf("public-client"))
        val result = extractClientAuthentication(body, emptyMap())
        assertTrue(result.isOk)
        assertTrue(result.value.clientAuthentication is ClientAuthenticationConfig.None)
    }

    @Test
    fun clientAuth_empty_accepts_asAnonymous() {
        val result = extractClientAuthentication(emptyMap(), emptyMap())
        assertTrue(result.isOk)
        assertEquals(ClientAuthenticationConfig.Anonymous, result.value.clientAuthentication)
    }
}
