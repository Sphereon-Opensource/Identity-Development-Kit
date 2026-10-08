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

package com.sphereon.oauth2.common.model

import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkSet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClientRegistrationSerializationTest {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = false
        }

    @Test
    fun `test serialize ClientRegistration with all RFC 7591 fields`() {
        val jwkSet =
            JwkSet(
                keys =
                    arrayOf(
                        Jwk(
                            kty = JwaKeyType.RSA,
                            kid = "test-key-1",
                            use = "sig",
                            alg = JwaAlgorithm.RS256,
                        ),
                    ),
            )

        val registration =
            ClientRegistration(
                clientId = "test-client-id",
                clientSecret = "test-secret",
                clientName = "Test Client",
                clientUri = "https://example.com",
                logoUri = "https://example.com/logo.png",
                clientType = ClientType.CONFIDENTIAL,
                grantTypes = listOf(GrantType.AUTHORIZATION_CODE, GrantType.REFRESH_TOKEN),
                responseTypes = listOf(ResponseType.CODE),
                redirectUris = listOf("https://example.com/callback"),
                allowedScopes = listOf("openid", "profile"),
                scope = "openid profile",
                tokenEndpointAuthMethod = ClientAuthenticationMethod.CLIENT_SECRET_POST,
                jwks = jwkSet,
                jwksUri = "https://example.com/jwks",
                contacts = listOf("admin@example.com"),
                tosUri = "https://example.com/tos",
                policyUri = "https://example.com/policy",
                softwareId = "software-123",
                softwareVersion = "1.0.0",
                softwareStatement = "eyJhbGc...",
                requirePkce = true,
                requirePushedAuthorizationRequests = false,
                dpopBoundAccessTokens = true,
                accessTokenLifetime = 7200,
                refreshTokenLifetime = 86400,
                authorizationCodeLifetime = 300,
            )

        val serialized = json.encodeToString(registration)
        println("Serialized ClientRegistration:\n$serialized")

        // Verify all RFC 7591 fields are present
        val jsonObject = json.parseToJsonElement(serialized).jsonObject
        assertEquals("test-client-id", jsonObject["client_id"]?.toString()?.trim('"'))
        assertEquals("test-secret", jsonObject["client_secret"]?.toString()?.trim('"'))
        assertEquals("Test Client", jsonObject["client_name"]?.toString()?.trim('"'))
        assertEquals("https://example.com", jsonObject["client_uri"]?.toString()?.trim('"'))
        assertEquals("https://example.com/logo.png", jsonObject["logo_uri"]?.toString()?.trim('"'))
        assertEquals("openid profile", jsonObject["scope"]?.toString()?.trim('"'))
        assertNotNull(jsonObject["contacts"])
        assertEquals("https://example.com/tos", jsonObject["tos_uri"]?.toString()?.trim('"'))
        assertEquals("https://example.com/policy", jsonObject["policy_uri"]?.toString()?.trim('"'))
        assertEquals("software-123", jsonObject["software_id"]?.toString()?.trim('"'))
        assertEquals("1.0.0", jsonObject["software_version"]?.toString()?.trim('"'))
        assertEquals("eyJhbGc...", jsonObject["software_statement"]?.toString()?.trim('"'))
        assertNotNull(jsonObject["jwks"])
        assertEquals("https://example.com/jwks", jsonObject["jwks_uri"]?.toString()?.trim('"'))
    }

    @Test
    fun `test deserialize ClientRegistration with all RFC 7591 fields`() {
        val jsonString =
            """
            {
                "client_id": "test-client-id",
                "client_secret": "test-secret",
                "client_name": "Test Client",
                "client_uri": "https://example.com",
                "logo_uri": "https://example.com/logo.png",
                "client_type": "CONFIDENTIAL",
                "grant_types": ["authorization_code", "refresh_token"],
                "response_types": ["code"],
                "redirect_uris": ["https://example.com/callback"],
                "allowed_scopes": ["openid", "profile"],
                "scope": "openid profile",
                "token_endpoint_auth_method": "CLIENT_SECRET_POST",
                "jwks_uri": "https://example.com/jwks",
                "contacts": ["admin@example.com"],
                "tos_uri": "https://example.com/tos",
                "policy_uri": "https://example.com/policy",
                "software_id": "software-123",
                "software_version": "1.0.0",
                "software_statement": "eyJhbGc...",
                "require_pkce": true,
                "require_pushed_authorization_requests": false,
                "dpop_bound_access_tokens": true,
                "access_token_lifetime": 7200,
                "refresh_token_lifetime": 86400,
                "authorization_code_lifetime": 300
            }
            """.trimIndent()

        val registration = json.decodeFromString<ClientRegistration>(jsonString)

        assertEquals("test-client-id", registration.clientId)
        assertEquals("test-secret", registration.clientSecret)
        assertEquals("Test Client", registration.clientName)
        assertEquals("https://example.com", registration.clientUri)
        assertEquals("https://example.com/logo.png", registration.logoUri)
        assertEquals(ClientType.CONFIDENTIAL, registration.clientType)
        assertEquals(2, registration.grantTypes.size)
        assertTrue(registration.grantTypes.contains(GrantType.AUTHORIZATION_CODE))
        assertEquals(1, registration.responseTypes.size)
        assertEquals(1, registration.redirectUris.size)
        assertEquals("openid profile", registration.scope)
        assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_POST, registration.tokenEndpointAuthMethod)
        assertEquals("https://example.com/jwks", registration.jwksUri)
        assertEquals(1, registration.contacts?.size)
        assertEquals("admin@example.com", registration.contacts?.first())
        assertEquals("https://example.com/tos", registration.tosUri)
        assertEquals("https://example.com/policy", registration.policyUri)
        assertEquals("software-123", registration.softwareId)
        assertEquals("1.0.0", registration.softwareVersion)
        assertEquals("eyJhbGc...", registration.softwareStatement)
        assertTrue(registration.requirePkce)
        assertTrue(registration.dpopBoundAccessTokens)
        assertEquals(7200, registration.accessTokenLifetime)
        assertEquals(86400, registration.refreshTokenLifetime)
        assertEquals(300, registration.authorizationCodeLifetime)
    }

    @Test
    fun `test ClientRegistration captures unknown extension parameters`() {
        val jsonString =
            """
            {
                "client_id": "test-client-id",
                "grant_types": ["authorization_code"],
                "custom_extension": "custom_value",
                "another_extension": 12345,
                "nested_extension": {
                    "nested_key": "nested_value"
                }
            }
            """.trimIndent()

        val registration = json.decodeFromString<ClientRegistration>(jsonString)

        assertEquals("test-client-id", registration.clientId)
        assertEquals(3, registration.additionalParameters.size)
        assertEquals("custom_value", (registration.additionalParameters["custom_extension"] as? JsonPrimitive)?.content)
        assertNotNull(registration.additionalParameters["another_extension"])
        assertNotNull(registration.additionalParameters["nested_extension"])
    }

    @Test
    fun `test ClientRegistration serialization preserves unknown parameters`() {
        val jsonString =
            """
            {
                "client_id": "test-client-id",
                "grant_types": ["authorization_code"],
                "custom_field": "custom_value"
            }
            """.trimIndent()

        val registration = json.decodeFromString<ClientRegistration>(jsonString)
        val reserialized = json.encodeToString(registration)
        val reserializedObj = json.parseToJsonElement(reserialized).jsonObject

        assertEquals("test-client-id", reserializedObj["client_id"]?.toString()?.trim('"'))
        assertEquals("custom_value", reserializedObj["custom_field"]?.toString()?.trim('"'))
    }

    @Test
    fun `test ClientRegistration minimal required fields`() {
        val jsonString =
            """
            {
                "client_id": "minimal-client",
                "grant_types": ["client_credentials"]
            }
            """.trimIndent()

        val registration = json.decodeFromString<ClientRegistration>(jsonString)

        assertEquals("minimal-client", registration.clientId)
        assertEquals(1, registration.grantTypes.size)
        assertEquals(GrantType.CLIENT_CREDENTIALS, registration.grantTypes.first())
        assertEquals(ClientType.CONFIDENTIAL, registration.clientType) // default
        assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, registration.tokenEndpointAuthMethod) // default
    }

    @Test
    fun `test ClientRegistration with JwkSet instead of List`() {
        val jwkSet =
            JwkSet(
                keys =
                    arrayOf(
                        Jwk(kty = JwaKeyType.RSA, kid = "key-1"),
                        Jwk(kty = JwaKeyType.EC, kid = "key-2"),
                    ),
            )

        val registration =
            ClientRegistration(
                clientId = "test-client",
                grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                jwks = jwkSet,
            )

        val serialized = json.encodeToString(registration)
        val deserialized = json.decodeFromString<ClientRegistration>(serialized)

        assertNotNull(deserialized.jwks)
        assertEquals(2, deserialized.jwks?.keys?.size)
        assertEquals(
            "key-1",
            deserialized.jwks
                ?.keys
                ?.get(0)
                ?.kid,
        )
        assertEquals(
            "key-2",
            deserialized.jwks
                ?.keys
                ?.get(1)
                ?.kid,
        )
    }
}

class ClientRegistrationValidationTest {
    @Test
    fun `test validateClientRegistration accepts valid registration`() {
        val registration =
            ClientRegistration(
                clientId = "valid-client",
                clientName = "Valid Client",
                grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                redirectUris = listOf("https://example.com/callback"),
                scope = "openid profile",
                contacts = listOf("admin@example.com"),
                accessTokenLifetime = 3600,
                authorizationCodeLifetime = 600,
                refreshTokenLifetime = 86400,
            )

        val result = validateClientRegistration(registration)
        assertTrue(result.errors.isEmpty(), "Expected no validation errors but got: ${result.errors}")
    }

    @Test
    fun `test validateClientRegistration rejects empty client_id`() {
        val registration =
            ClientRegistration(
                clientId = "",
                grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
            )

        val result = validateClientRegistration(registration)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.message.contains("client_id") })
    }

    @Test
    fun `test validateClientRegistration requires redirect_uris for authorization_code`() {
        val registration =
            ClientRegistration(
                clientId = "test-client",
                grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                redirectUris = emptyList(),
            )

        val result = validateClientRegistration(registration)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.message.contains("redirect_uris") })
    }

    @Test
    fun `test validateClientRegistration rejects negative token lifetimes`() {
        val registration =
            ClientRegistration(
                clientId = "test-client",
                grantTypes = listOf(GrantType.CLIENT_CREDENTIALS),
                accessTokenLifetime = -100,
            )

        val result = validateClientRegistration(registration)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.message.contains("positive") })
    }

    @Test
    fun `test validateClientRegistration rejects empty grant_types`() {
        val registration =
            ClientRegistration(
                clientId = "test-client",
                grantTypes = emptyList(),
            )

        val result = validateClientRegistration(registration)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.message.contains("grant_types") })
    }
}
