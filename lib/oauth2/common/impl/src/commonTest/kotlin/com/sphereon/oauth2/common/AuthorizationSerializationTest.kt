/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.common

import com.sphereon.oauth2.common.model.AuthorizationErrorResponse
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.oauth2.common.model.PushedAuthorizationRequest
import com.sphereon.oauth2.common.model.PushedAuthorizationResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthorizationSerializationTest {
    @Test
    fun `test AuthorizationRequest serialization with standard fields`() {
        val request =
            AuthorizationRequest(
                clientId = "test_client",
                redirectUri = "https://example.com/callback",
                responseType = "code",
                scope = "read write",
                state = "test_state",
                codeChallenge = "test_challenge",
                codeChallengeMethod = "S256",
            )

        val json = Json.encodeToString(AuthorizationRequest.serializer(), request)

        assertTrue(json.contains("\"client_id\":\"test_client\""))
        assertTrue(json.contains("\"redirect_uri\":\"https://example.com/callback\""))
        assertTrue(json.contains("\"response_type\":\"code\""))
        assertTrue(json.contains("\"scope\":\"read write\""))
        assertTrue(json.contains("\"state\":\"test_state\""))
        assertTrue(json.contains("\"code_challenge\":\"test_challenge\""))
        assertTrue(json.contains("\"code_challenge_method\":\"S256\""))
    }

    @Test
    fun `test AuthorizationRequest deserialization with additional parameters`() {
        val json =
            """
            {
                "client_id": "test_client",
                "redirect_uri": "https://example.com/callback",
                "response_type": "code",
                "custom_param": "custom_value",
                "custom_number": 42
            }
            """.trimIndent()

        val request = Json.decodeFromString(AuthorizationRequest.serializer(), json)

        assertEquals("test_client", request.clientId)
        assertEquals("https://example.com/callback", request.redirectUri)
        assertEquals("code", request.responseType)
        assertEquals(2, request.additionalParameters.size)
        assertTrue(request.additionalParameters.containsKey("custom_param"))
        assertTrue(request.additionalParameters.containsKey("custom_number"))
    }

    @Test
    fun `test AuthorizationRequest round-trip with additional parameters`() {
        val original =
            AuthorizationRequest(
                clientId = "test_client",
                redirectUri = "https://example.com/callback",
                responseType = "code",
                additionalParameters =
                    mapOf(
                        "custom_field" to JsonPrimitive("custom_value"),
                    ),
            )

        val json = Json.encodeToString(AuthorizationRequest.serializer(), original)
        val decoded = Json.decodeFromString(AuthorizationRequest.serializer(), json)

        assertEquals(original.clientId, decoded.clientId)
        assertEquals(original.redirectUri, decoded.redirectUri)
        assertEquals(1, decoded.additionalParameters.size)
        assertTrue(decoded.additionalParameters.containsKey("custom_field"))
    }

    @Test
    fun `test AuthorizationRequest with OpenID Connect parameters`() {
        val request =
            AuthorizationRequest(
                clientId = "test_client",
                redirectUri = "https://example.com/callback",
                responseType = "code",
                scope = "openid profile email",
                nonce = "test_nonce",
                responseMode = "fragment",
            )

        val json = Json.encodeToString(AuthorizationRequest.serializer(), request)
        val decoded = Json.decodeFromString(AuthorizationRequest.serializer(), json)

        assertEquals(request.nonce, decoded.nonce)
        assertEquals(request.responseMode, decoded.responseMode)
        assertEquals(request.scope, decoded.scope)
    }

    @Test
    fun `test AuthorizationRequest with DPoP`() {
        val json =
            """
            {
                "client_id": "test_client",
                "redirect_uri": "https://example.com/callback",
                "response_type": "code",
                "dpop_jkt": "test_dpop_thumbprint"
            }
            """.trimIndent()

        val request = Json.decodeFromString(AuthorizationRequest.serializer(), json)

        assertEquals("test_dpop_thumbprint", request.dpopJkt)
    }

    @Test
    fun `test AuthorizationResponse serialization`() {
        val response =
            AuthorizationResponse(
                code = "test_auth_code",
                state = "test_state",
            )

        val json = Json.encodeToString(AuthorizationResponse.serializer(), response)

        assertTrue(json.contains("\"code\":\"test_auth_code\""))
        assertTrue(json.contains("\"state\":\"test_state\""))
    }

    @Test
    fun `test AuthorizationResponse deserialization with additional parameters`() {
        val json =
            """
            {
                "code": "test_auth_code",
                "state": "test_state",
                "iss": "https://auth.example.com",
                "custom_param": "custom_value"
            }
            """.trimIndent()

        val response = Json.decodeFromString(AuthorizationResponse.serializer(), json)

        assertEquals("test_auth_code", response.code)
        assertEquals("test_state", response.state)
        assertEquals(2, response.additionalParameters.size)
        assertTrue(response.additionalParameters.containsKey("iss"))
        assertTrue(response.additionalParameters.containsKey("custom_param"))
    }

    @Test
    fun `test AuthorizationErrorResponse serialization`() {
        val error =
            AuthorizationErrorResponse(
                error = "access_denied",
                errorDescription = "The user denied access",
                errorUri = "https://example.com/error",
                state = "test_state",
            )

        val json = Json.encodeToString(AuthorizationErrorResponse.serializer(), error)

        assertTrue(json.contains("\"error\":\"access_denied\""))
        assertTrue(json.contains("\"error_description\":\"The user denied access\""))
        assertTrue(json.contains("\"error_uri\":\"https://example.com/error\""))
        assertTrue(json.contains("\"state\":\"test_state\""))
    }

    @Test
    fun `test AuthorizationErrorResponse deserialization with additional parameters`() {
        val json =
            """
            {
                "error": "invalid_request",
                "error_description": "Missing parameter",
                "custom_error_field": "custom_value"
            }
            """.trimIndent()

        val error = Json.decodeFromString(AuthorizationErrorResponse.serializer(), json)

        assertEquals("invalid_request", error.error)
        assertEquals("Missing parameter", error.errorDescription)
        assertEquals(1, error.additionalParameters.size)
        assertTrue(error.additionalParameters.containsKey("custom_error_field"))
    }

    @Test
    fun `test PushedAuthorizationRequest serialization`() {
        val request =
            PushedAuthorizationRequest(
                requestUri = "urn:ietf:params:oauth:request_uri:test",
                clientId = "test_client",
            )

        val json = Json.encodeToString(PushedAuthorizationRequest.serializer(), request)

        assertTrue(json.contains("\"request_uri\":\"urn:ietf:params:oauth:request_uri:test\""))
        assertTrue(json.contains("\"client_id\":\"test_client\""))
    }

    @Test
    fun `test PushedAuthorizationResponse deserialization with additional parameters`() {
        val json =
            """
            {
                "request_uri": "urn:ietf:params:oauth:request_uri:test",
                "expires_in": 60,
                "custom_field": "custom_value"
            }
            """.trimIndent()

        val response = Json.decodeFromString(PushedAuthorizationResponse.serializer(), json)

        assertEquals("urn:ietf:params:oauth:request_uri:test", response.requestUri)
        assertEquals(60, response.expiresIn)
        assertEquals(1, response.additionalParameters.size)
        assertTrue(response.additionalParameters.containsKey("custom_field"))
    }
}
