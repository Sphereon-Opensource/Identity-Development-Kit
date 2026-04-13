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

import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthorizationServerMetadataSerializationTest {
    @Test
    fun `test metadata serialization with standard fields`() {
        val metadata =
            AuthorizationServerMetadata(
                issuer = "https://auth.example.com",
                tokenEndpoint = "https://auth.example.com/token",
                authorizationEndpoint = "https://auth.example.com/authorize",
                jwksUri = "https://auth.example.com/jwks",
                grantTypesSupported = listOf("authorization_code", "refresh_token"),
                codeChallengeMethodsSupported = listOf("S256", "plain"),
            )

        val json = Json.encodeToString(AuthorizationServerMetadata.serializer(), metadata)

        assertTrue(json.contains("\"issuer\":\"https://auth.example.com\""))
        assertTrue(json.contains("\"token_endpoint\":\"https://auth.example.com/token\""))
        assertTrue(json.contains("\"authorization_endpoint\":\"https://auth.example.com/authorize\""))
        assertTrue(json.contains("\"jwks_uri\":\"https://auth.example.com/jwks\""))
        assertTrue(json.contains("\"grant_types_supported\":["))
        assertTrue(json.contains("\"code_challenge_methods_supported\":["))
    }

    @Test
    fun `test metadata deserialization with additional fields`() {
        val json =
            """
            {
                "issuer": "https://auth.example.com",
                "token_endpoint": "https://auth.example.com/token",
                "authorization_endpoint": "https://auth.example.com/authorize",
                "custom_discovery_field": "custom_value",
                "custom_array_field": ["item1", "item2"],
                "custom_boolean_field": true
            }
            """.trimIndent()

        val metadata = Json.decodeFromString(AuthorizationServerMetadata.serializer(), json)

        assertEquals("https://auth.example.com", metadata.issuer)
        assertEquals("https://auth.example.com/token", metadata.tokenEndpoint)
        assertEquals("https://auth.example.com/authorize", metadata.authorizationEndpoint)
        assertEquals(3, metadata.additionalMetadata.size)
        assertTrue(metadata.additionalMetadata.containsKey("custom_discovery_field"))
        assertTrue(metadata.additionalMetadata.containsKey("custom_array_field"))
        assertTrue(metadata.additionalMetadata.containsKey("custom_boolean_field"))
    }

    @Test
    fun `test metadata round-trip with additional metadata`() {
        val original =
            AuthorizationServerMetadata(
                issuer = "https://auth.example.com",
                tokenEndpoint = "https://auth.example.com/token",
                dpopSigningAlgValuesSupported = listOf("ES256", "RS256"),
                additionalMetadata =
                    mapOf(
                        "custom_endpoint" to JsonPrimitive("https://auth.example.com/custom"),
                        "custom_flag" to JsonPrimitive(true),
                    ),
            )

        val json = Json.encodeToString(AuthorizationServerMetadata.serializer(), original)
        val decoded = Json.decodeFromString(AuthorizationServerMetadata.serializer(), json)

        assertEquals(original.issuer, decoded.issuer)
        assertEquals(original.tokenEndpoint, decoded.tokenEndpoint)
        assertEquals(original.dpopSigningAlgValuesSupported, decoded.dpopSigningAlgValuesSupported)
        assertEquals(2, decoded.additionalMetadata.size)
        assertTrue(decoded.additionalMetadata.containsKey("custom_endpoint"))
        assertTrue(decoded.additionalMetadata.containsKey("custom_flag"))
    }

    @Test
    fun `test metadata with OpenID Connect Discovery fields`() {
        val json =
            """
            {
                "issuer": "https://auth.example.com",
                "token_endpoint": "https://auth.example.com/token",
                "authorization_endpoint": "https://auth.example.com/authorize",
                "jwks_uri": "https://auth.example.com/jwks",
                "userinfo_endpoint": "https://auth.example.com/userinfo",
                "scopes_supported": ["openid", "profile", "email"],
                "response_types_supported": ["code", "token", "id_token"]
            }
            """.trimIndent()

        val metadata = Json.decodeFromString(AuthorizationServerMetadata.serializer(), json)

        assertEquals("https://auth.example.com", metadata.issuer)
        assertEquals("https://auth.example.com/jwks", metadata.jwksUri)
        // OpenID Connect specific fields are now modeled as explicit properties
        assertEquals("https://auth.example.com/userinfo", metadata.userinfoEndpoint)
        assertEquals(listOf("openid", "profile", "email"), metadata.scopesSupported)
        assertEquals(listOf("code", "token", "id_token"), metadata.responseTypesSupported)
        assertTrue(metadata.additionalMetadata.isEmpty(), "All fields should be known, no additional metadata")
    }

    @Test
    fun `test metadata with PAR support`() {
        val metadata =
            AuthorizationServerMetadata(
                issuer = "https://auth.example.com",
                tokenEndpoint = "https://auth.example.com/token",
                pushedAuthorizationRequestEndpoint = "https://auth.example.com/par",
                requirePushedAuthorizationRequests = true,
            )

        val json = Json.encodeToString(AuthorizationServerMetadata.serializer(), metadata)
        val decoded = Json.decodeFromString(AuthorizationServerMetadata.serializer(), json)

        assertEquals(metadata.pushedAuthorizationRequestEndpoint, decoded.pushedAuthorizationRequestEndpoint)
        assertEquals(metadata.requirePushedAuthorizationRequests, decoded.requirePushedAuthorizationRequests)
    }

    @Test
    fun `test metadata with DPoP support`() {
        val json =
            """
            {
                "issuer": "https://auth.example.com",
                "token_endpoint": "https://auth.example.com/token",
                "dpop_signing_alg_values_supported": ["ES256", "RS256", "PS256"]
            }
            """.trimIndent()

        val metadata = Json.decodeFromString(AuthorizationServerMetadata.serializer(), json)

        assertEquals(3, metadata.dpopSigningAlgValuesSupported?.size)
        assertTrue(metadata.dpopSigningAlgValuesSupported?.contains("ES256") == true)
        assertTrue(metadata.dpopSigningAlgValuesSupported?.contains("RS256") == true)
        assertTrue(metadata.dpopSigningAlgValuesSupported?.contains("PS256") == true)
    }

    @Test
    fun `test metadata with introspection endpoint`() {
        val metadata =
            AuthorizationServerMetadata(
                issuer = "https://auth.example.com",
                tokenEndpoint = "https://auth.example.com/token",
                introspectionEndpoint = "https://auth.example.com/introspect",
                introspectionEndpointAuthMethodsSupported = listOf("client_secret_basic", "private_key_jwt"),
            )

        val json = Json.encodeToString(AuthorizationServerMetadata.serializer(), metadata)
        val decoded = Json.decodeFromString(AuthorizationServerMetadata.serializer(), json)

        assertEquals(metadata.introspectionEndpoint, decoded.introspectionEndpoint)
        assertEquals(metadata.introspectionEndpointAuthMethodsSupported, decoded.introspectionEndpointAuthMethodsSupported)
    }
}
