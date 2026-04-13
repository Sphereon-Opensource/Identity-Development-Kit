package com.sphereon.oauth2.common.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests that AuthorizationRequest correctly serializes and deserializes
 * OIDC-specific parameters per OpenID Connect Core Section 3.1.2.1.
 */
class AuthorizationRequestOidcTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serializesOidcParameters() {
        val request = AuthorizationRequest(
            clientId = "test-client",
            redirectUri = "https://client.example.com/callback",
            responseType = "code",
            scope = "openid profile email",
            nonce = "n-0S6_WzA2Mj",
            prompt = "consent",
            loginHint = "user@example.com",
            maxAge = 3600,
            uiLocales = "en de",
            idTokenHint = "eyJhbGciOiJFUzI1NiJ9...",
            acrValues = "urn:mace:incommon:iap:silver",
            display = "page"
        )

        val serialized = json.encodeToString(AuthorizationRequest.serializer(), request)
        val obj = json.parseToJsonElement(serialized).jsonObject

        assertEquals("consent", obj["prompt"]?.jsonPrimitive?.content)
        assertEquals("user@example.com", obj["login_hint"]?.jsonPrimitive?.content)
        assertEquals("3600", obj["max_age"]?.jsonPrimitive?.content)
        assertEquals("en de", obj["ui_locales"]?.jsonPrimitive?.content)
        assertEquals("eyJhbGciOiJFUzI1NiJ9...", obj["id_token_hint"]?.jsonPrimitive?.content)
        assertEquals("urn:mace:incommon:iap:silver", obj["acr_values"]?.jsonPrimitive?.content)
        assertEquals("page", obj["display"]?.jsonPrimitive?.content)
        assertEquals("n-0S6_WzA2Mj", obj["nonce"]?.jsonPrimitive?.content)
    }

    @Test
    fun deserializesOidcParameters() {
        val input = """
        {
            "client_id": "test-client",
            "redirect_uri": "https://client.example.com/callback",
            "response_type": "code",
            "scope": "openid profile",
            "nonce": "abc123",
            "prompt": "login",
            "login_hint": "john@example.com",
            "max_age": 7200,
            "ui_locales": "fr en",
            "id_token_hint": "some.jwt.token",
            "acr_values": "urn:example:acr",
            "display": "popup"
        }
        """.trimIndent()

        val request = json.decodeFromString(AuthorizationRequest.serializer(), input)

        assertEquals("test-client", request.clientId)
        assertEquals("openid profile", request.scope)
        assertEquals("abc123", request.nonce)
        assertEquals("login", request.prompt)
        assertEquals("john@example.com", request.loginHint)
        assertEquals(7200L, request.maxAge)
        assertEquals("fr en", request.uiLocales)
        assertEquals("some.jwt.token", request.idTokenHint)
        assertEquals("urn:example:acr", request.acrValues)
        assertEquals("popup", request.display)
    }

    @Test
    fun oidcParametersNullWhenAbsent() {
        val input = """
        {
            "client_id": "test-client",
            "redirect_uri": "https://client.example.com/callback",
            "response_type": "code"
        }
        """.trimIndent()

        val request = json.decodeFromString(AuthorizationRequest.serializer(), input)

        assertNull(request.nonce)
        assertNull(request.prompt)
        assertNull(request.loginHint)
        assertNull(request.maxAge)
        assertNull(request.uiLocales)
        assertNull(request.idTokenHint)
        assertNull(request.acrValues)
        assertNull(request.display)
    }

    @Test
    fun nullOidcParametersNotSerialized() {
        val request = AuthorizationRequest(
            clientId = "test-client",
            redirectUri = "https://client.example.com/callback",
            responseType = "code"
        )

        val serialized = json.encodeToString(AuthorizationRequest.serializer(), request)
        val obj = json.parseToJsonElement(serialized).jsonObject

        assertTrue("prompt" !in obj)
        assertTrue("login_hint" !in obj)
        assertTrue("max_age" !in obj)
        assertTrue("ui_locales" !in obj)
        assertTrue("id_token_hint" !in obj)
        assertTrue("acr_values" !in obj)
        assertTrue("display" !in obj)
    }

    @Test
    fun oidcParametersSurviveRoundTrip() {
        val original = AuthorizationRequest(
            clientId = "roundtrip-client",
            redirectUri = "https://client.example.com/callback",
            responseType = "code",
            scope = "openid profile email",
            state = "state123",
            nonce = "nonce456",
            prompt = "consent login",
            loginHint = "roundtrip@example.com",
            maxAge = 1800,
            uiLocales = "en",
            display = "touch"
        )

        val serialized = json.encodeToString(AuthorizationRequest.serializer(), original)
        val deserialized = json.decodeFromString(AuthorizationRequest.serializer(), serialized)

        assertEquals(original.clientId, deserialized.clientId)
        assertEquals(original.redirectUri, deserialized.redirectUri)
        assertEquals(original.scope, deserialized.scope)
        assertEquals(original.nonce, deserialized.nonce)
        assertEquals(original.prompt, deserialized.prompt)
        assertEquals(original.loginHint, deserialized.loginHint)
        assertEquals(original.maxAge, deserialized.maxAge)
        assertEquals(original.uiLocales, deserialized.uiLocales)
        assertEquals(original.display, deserialized.display)
    }

    @Test
    fun unknownParametersCapturedInAdditionalParameters() {
        val input = """
        {
            "client_id": "test-client",
            "redirect_uri": "https://client.example.com/callback",
            "response_type": "code",
            "prompt": "consent",
            "custom_param": "custom_value"
        }
        """.trimIndent()

        val request = json.decodeFromString(AuthorizationRequest.serializer(), input)

        assertEquals("consent", request.prompt)
        assertNotNull(request.additionalParameters["custom_param"])
        // prompt should NOT be in additionalParameters
        assertNull(request.additionalParameters["prompt"])
    }
}
