package com.sphereon.oauth2.common

import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenIntrospectionSerializationTest {

    @Test
    fun `test TokenIntrospectionResponse with single aud serializes as string`() {
        val response = TokenIntrospectionResponse(
            active = true,
            scope = "read write",
            clientId = "test_client",
            aud = listOf("https://api.example.com")
        )

        val json = Json.encodeToString(TokenIntrospectionResponse.serializer(), response)

        // Single audience should serialize as string, not array
        assertTrue(json.contains("\"aud\":\"https://api.example.com\""))
        assertTrue(!json.contains("\"aud\":["))
    }

    @Test
    fun `test TokenIntrospectionResponse with multiple aud serializes as array`() {
        val response = TokenIntrospectionResponse(
            active = true,
            scope = "read write",
            clientId = "test_client",
            aud = listOf("https://api1.example.com", "https://api2.example.com")
        )

        val json = Json.encodeToString(TokenIntrospectionResponse.serializer(), response)

        // Multiple audiences should serialize as array
        assertTrue(json.contains("\"aud\":["))
        assertTrue(json.contains("https://api1.example.com"))
        assertTrue(json.contains("https://api2.example.com"))
    }

    @Test
    fun `test TokenIntrospectionResponse deserializes aud from string`() {
        val json = """
            {
                "active": true,
                "scope": "read write",
                "aud": "https://api.example.com"
            }
        """.trimIndent()

        val response = Json.decodeFromString(TokenIntrospectionResponse.serializer(), json)

        assertEquals(true, response.active)
        assertEquals(listOf("https://api.example.com"), response.aud)
    }

    @Test
    fun `test TokenIntrospectionResponse deserializes aud from array`() {
        val json = """
            {
                "active": true,
                "scope": "read write",
                "aud": ["https://api1.example.com", "https://api2.example.com"]
            }
        """.trimIndent()

        val response = Json.decodeFromString(TokenIntrospectionResponse.serializer(), json)

        assertEquals(true, response.active)
        assertEquals(listOf("https://api1.example.com", "https://api2.example.com"), response.aud)
    }

    @Test
    fun `test TokenIntrospectionResponse round-trip with single aud`() {
        val original = TokenIntrospectionResponse(
            active = true,
            scope = "read write",
            clientId = "test_client",
            aud = listOf("https://api.example.com"),
            sub = "user123",
            iss = "https://auth.example.com"
        )

        val json = Json.encodeToString(TokenIntrospectionResponse.serializer(), original)
        val decoded = Json.decodeFromString(TokenIntrospectionResponse.serializer(), json)

        assertEquals(original.active, decoded.active)
        assertEquals(original.aud, decoded.aud)
        assertEquals(original.sub, decoded.sub)
        assertEquals(original.iss, decoded.iss)

        // Verify single aud serialized as string
        assertTrue(json.contains("\"aud\":\"https://api.example.com\""))
    }

    @Test
    fun `test TokenIntrospectionResponse round-trip with multiple aud`() {
        val original = TokenIntrospectionResponse(
            active = true,
            scope = "read write",
            aud = listOf("https://api1.example.com", "https://api2.example.com")
        )

        val json = Json.encodeToString(TokenIntrospectionResponse.serializer(), original)
        val decoded = Json.decodeFromString(TokenIntrospectionResponse.serializer(), json)

        assertEquals(original.active, decoded.active)
        assertEquals(original.aud, decoded.aud)

        // Verify multiple aud serialized as array
        assertTrue(json.contains("\"aud\":["))
    }

    @Test
    fun `test TokenIntrospectionResponse with all standard fields`() {
        val now = Clock.System.now().epochSeconds

        val response = TokenIntrospectionResponse(
            active = true,
            scope = "read write delete",
            clientId = "test_client",
            username = "john.doe",
            tokenType = "Bearer",
            exp = now + 3600,
            iat = now,
            nbf = now,
            sub = "user123",
            aud = listOf("https://api.example.com"),
            iss = "https://auth.example.com",
            jti = "token-id-123"
        )

        val json = Json.encodeToString(TokenIntrospectionResponse.serializer(), response)
        val decoded = Json.decodeFromString(TokenIntrospectionResponse.serializer(), json)

        assertEquals(response.active, decoded.active)
        assertEquals(response.scope, decoded.scope)
        assertEquals(response.clientId, decoded.clientId)
        assertEquals(response.username, decoded.username)
        assertEquals(response.tokenType, decoded.tokenType)
        assertEquals(response.exp, decoded.exp)
        assertEquals(response.iat, decoded.iat)
        assertEquals(response.nbf, decoded.nbf)
        assertEquals(response.sub, decoded.sub)
        assertEquals(response.aud, decoded.aud)
        assertEquals(response.iss, decoded.iss)
        assertEquals(response.jti, decoded.jti)
    }

    @Test
    fun `test TokenIntrospectionResponse with DPoP confirmation`() {
        val json = """
            {
                "active": true,
                "scope": "read",
                "cnf": {
                    "jkt": "0ZcOCORZNYy-DWpqq30jZyJGHTN0d2HglBV3uiguA4I"
                }
            }
        """.trimIndent()

        val response = Json.decodeFromString(TokenIntrospectionResponse.serializer(), json)

        assertEquals(true, response.active)
        assertEquals("0ZcOCORZNYy-DWpqq30jZyJGHTN0d2HglBV3uiguA4I", response.cnf?.jkt)
    }

    @Test
    fun `test TokenIntrospectionResponse inactive token`() {
        val json = """
            {
                "active": false
            }
        """.trimIndent()

        val response = Json.decodeFromString(TokenIntrospectionResponse.serializer(), json)

        assertEquals(false, response.active)
    }
}
