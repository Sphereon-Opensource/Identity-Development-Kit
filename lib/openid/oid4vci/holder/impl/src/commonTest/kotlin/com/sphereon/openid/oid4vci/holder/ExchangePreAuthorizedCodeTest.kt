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

package com.sphereon.openid.oid4vci.holder

import com.sphereon.openid.oid4vci.holder.impl.ExchangePreAuthorizedCodeCommandImpl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Serialization/deserialization tests for ExchangePreAuthorizedCode.
 *
 * Pure serialization tests — no HTTP mocking required.
 */
class ExchangePreAuthorizedCodeTest {
    private val json = Json { ignoreUnknownKeys = true }

    // ============================================================================
    // TokenResponseWithContext deserialization
    // ============================================================================

    @Test
    fun deserializeMinimalTokenResponse() {
        val raw =
            """
            {
              "access_token": "eyJhbGciOiJSUzI1NiJ9",
              "token_type": "Bearer"
            }
            """.trimIndent()

        val response = json.decodeFromString(TokenResponseWithContext.serializer(), raw)

        assertEquals("eyJhbGciOiJSUzI1NiJ9", response.accessToken)
        assertEquals("Bearer", response.tokenType)
        assertNull(response.expiresIn)
        assertNull(response.cNonce)
        assertNull(response.cNonceExpiresIn)
        assertNull(response.authorizationDetails)
    }

    @Test
    fun deserializeFullTokenResponse() {
        val raw =
            """
            {
              "access_token": "eyJhbGciOiJSUzI1NiJ9.payload.sig",
              "token_type": "bearer",
              "expires_in": 3600,
              "c_nonce": "tZignsnFbp",
              "c_nonce_expires_in": 86400,
              "authorization_details": [
                {
                  "type": "openid_credential",
                  "credential_configuration_id": "UniversityDegreeCredential"
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString(TokenResponseWithContext.serializer(), raw)

        assertEquals("eyJhbGciOiJSUzI1NiJ9.payload.sig", response.accessToken)
        assertEquals("bearer", response.tokenType)
        assertEquals(3600, response.expiresIn)
        assertEquals("tZignsnFbp", response.cNonce)
        assertEquals(86400, response.cNonceExpiresIn)
        assertNotNull(response.authorizationDetails)
        assertEquals(1, response.authorizationDetails!!.size)
    }

    @Test
    fun deserializeTokenResponseWithExtraFields() {
        // Extra fields should be ignored via ignoreUnknownKeys
        val raw =
            """
            {
              "access_token": "tok123",
              "token_type": "Bearer",
              "refresh_token": "refresh_xyz",
              "scope": "openid"
            }
            """.trimIndent()

        val response = json.decodeFromString(TokenResponseWithContext.serializer(), raw)

        assertEquals("tok123", response.accessToken)
        assertEquals("Bearer", response.tokenType)
    }

    // ============================================================================
    // Grant type constant
    // ============================================================================

    @Test
    fun preAuthorizedCodeGrantTypeIsCorrect() {
        assertEquals(
            "urn:ietf:params:oauth:grant-type:pre-authorized_code",
            ExchangePreAuthorizedCodeCommandImpl.PRE_AUTHORIZED_CODE_GRANT_TYPE,
        )
    }

    // ============================================================================
    // ExchangePreAuthorizedCodeArgs validation
    // ============================================================================

    @Test
    fun argsWithAllOptionalFields() {
        val args =
            ExchangePreAuthorizedCodeArgs(
                tokenEndpoint = "https://issuer.example.com/token",
                preAuthorizedCode = "SplxlOBeZQQYbYS6WxSbIA",
                txCode = "123456",
                clientId = "wallet-client",
                redirectUri = "https://wallet.example.com/callback",
            )

        assertEquals("SplxlOBeZQQYbYS6WxSbIA", args.preAuthorizedCode)
        assertEquals("123456", args.txCode)
        assertEquals("wallet-client", args.clientId)
        assertEquals("https://wallet.example.com/callback", args.redirectUri)
    }

    @Test
    fun argsWithOnlyRequired() {
        val args =
            ExchangePreAuthorizedCodeArgs(
                tokenEndpoint = "https://issuer.example.com/token",
                preAuthorizedCode = "SplxlOBeZQQYbYS6WxSbIA",
            )

        assertEquals("SplxlOBeZQQYbYS6WxSbIA", args.preAuthorizedCode)
        assertNull(args.txCode)
        assertNull(args.clientId)
        assertNull(args.redirectUri)
    }
}
