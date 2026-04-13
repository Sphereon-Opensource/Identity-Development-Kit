package com.sphereon.oauth2.common

import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.model.TokenResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenSerializationTest {

    @Test
    fun `test TokenResponse serialization with standard fields`() {
        val response = TokenResponse(
            accessToken = "test_access_token",
            tokenType = "Bearer",
            expiresIn = 3600,
            refreshToken = "test_refresh_token",
            scope = "read write",
            idToken = "test_id_token"
        )

        val json = Json.encodeToString(TokenResponse.serializer(), response)

        assertTrue(json.contains("\"access_token\":\"test_access_token\""))
        assertTrue(json.contains("\"token_type\":\"Bearer\""))
        assertTrue(json.contains("\"expires_in\":3600"))
        assertTrue(json.contains("\"refresh_token\":\"test_refresh_token\""))
        assertTrue(json.contains("\"scope\":\"read write\""))
        assertTrue(json.contains("\"id_token\":\"test_id_token\""))
    }

    @Test
    fun `test TokenResponse deserialization with additional parameters`() {
        val json = """
            {
                "access_token": "test_token",
                "token_type": "Bearer",
                "expires_in": 3600,
                "custom_param_1": "custom_value",
                "custom_param_2": 42,
                "custom_param_3": true
            }
        """.trimIndent()

        val response = Json.decodeFromString(TokenResponse.serializer(), json)

        assertEquals("test_token", response.accessToken)
        assertEquals("Bearer", response.tokenType)
        assertEquals(3600, response.expiresIn)
        assertEquals(3, response.additionalParameters.size)
        assertTrue(response.additionalParameters.containsKey("custom_param_1"))
        assertTrue(response.additionalParameters.containsKey("custom_param_2"))
        assertTrue(response.additionalParameters.containsKey("custom_param_3"))
    }

    @Test
    fun `test TokenResponse round-trip with additional parameters`() {
        val original = TokenResponse(
            accessToken = "test_token",
            tokenType = "Bearer",
            expiresIn = 3600,
            additionalParameters = mapOf(
                "custom_field" to JsonPrimitive("custom_value")
            )
        )

        val json = Json.encodeToString(TokenResponse.serializer(), original)
        val decoded = Json.decodeFromString(TokenResponse.serializer(), json)

        assertEquals(original.accessToken, decoded.accessToken)
        assertEquals(original.tokenType, decoded.tokenType)
        assertEquals(original.expiresIn, decoded.expiresIn)
        assertEquals(1, decoded.additionalParameters.size)
        assertTrue(decoded.additionalParameters.containsKey("custom_field"))
    }

    @Test
    fun `test TokenResponse with OpenID4VCI extensions`() {
        val json = """
            {
                "access_token": "test_token",
                "token_type": "Bearer",
                "c_nonce": "test_nonce",
                "c_nonce_expires_in": 86400
            }
        """.trimIndent()

        val response = Json.decodeFromString(TokenResponse.serializer(), json)

        assertEquals("test_token", response.accessToken)
        assertEquals("Bearer", response.tokenType)
        assertEquals("test_nonce", response.cNonce)
        assertEquals(86400, response.cNonceExpiresIn)
    }

    @Test
    fun `test TokenRequest serialization with standard fields`() {
        val request = TokenRequest(
            grantType = "authorization_code",
            code = "test_code",
            redirectUri = "https://example.com/callback",
            codeVerifier = "test_verifier",
            clientId = "test_client"
        )

        val json = Json.encodeToString(TokenRequest.serializer(), request)

        assertTrue(json.contains("\"grant_type\":\"authorization_code\""))
        assertTrue(json.contains("\"code\":\"test_code\""))
        assertTrue(json.contains("\"redirect_uri\":\"https://example.com/callback\""))
        assertTrue(json.contains("\"code_verifier\":\"test_verifier\""))
        assertTrue(json.contains("\"client_id\":\"test_client\""))
    }

    @Test
    fun `test TokenRequest deserialization with additional parameters`() {
        val json = """
            {
                "grant_type": "authorization_code",
                "code": "test_code",
                "custom_extension": "extension_value",
                "custom_number": 123
            }
        """.trimIndent()

        val request = Json.decodeFromString(TokenRequest.serializer(), json)

        assertEquals("authorization_code", request.grantType)
        assertEquals("test_code", request.code)
        assertEquals(2, request.additionalParameters.size)
        assertTrue(request.additionalParameters.containsKey("custom_extension"))
        assertTrue(request.additionalParameters.containsKey("custom_number"))
    }

    @Test
    fun `test TokenRequest round-trip with additional parameters`() {
        val original = TokenRequest(
            grantType = "client_credentials",
            scope = "read write",
            additionalParameters = mapOf(
                "custom_field" to JsonPrimitive("custom_value"),
                "custom_array" to kotlinx.serialization.json.JsonArray(
                    listOf(JsonPrimitive("item1"), JsonPrimitive("item2"))
                )
            )
        )

        val json = Json.encodeToString(TokenRequest.serializer(), original)
        val decoded = Json.decodeFromString(TokenRequest.serializer(), json)

        assertEquals(original.grantType, decoded.grantType)
        assertEquals(original.scope, decoded.scope)
        assertEquals(2, decoded.additionalParameters.size)
        assertTrue(decoded.additionalParameters.containsKey("custom_field"))
        assertTrue(decoded.additionalParameters.containsKey("custom_array"))
    }

    @Test
    fun `test TokenRequest with refresh_token grant`() {
        val json = """
            {
                "grant_type": "refresh_token",
                "refresh_token": "test_refresh_token",
                "scope": "read"
            }
        """.trimIndent()

        val request = Json.decodeFromString(TokenRequest.serializer(), json)

        assertEquals("refresh_token", request.grantType)
        assertEquals("test_refresh_token", request.refreshToken)
        assertEquals("read", request.scope)
    }
}
