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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests that AuthorizationServerMetadata correctly serializes and deserializes
 * OIDC-specific fields added for OpenID Connect Discovery compliance.
 */
class AuthorizationServerMetadataOidcTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serializesOidcFields() {
        val metadata =
            AuthorizationServerMetadata(
                issuer = "https://auth.example.com",
                tokenEndpoint = "https://auth.example.com/token",
                userinfoEndpoint = "https://auth.example.com/userinfo",
                subjectTypesSupported = listOf("public"),
                idTokenSigningAlgValuesSupported = listOf("ES256", "RS256"),
                claimsSupported = listOf("sub", "name", "email", "email_verified"),
                claimsParameterSupported = false,
                requestParameterSupported = true,
                requestUriParameterSupported = false,
            )

        val serialized = json.encodeToString(AuthorizationServerMetadata.serializer(), metadata)
        val obj = json.parseToJsonElement(serialized).jsonObject

        assertEquals("https://auth.example.com/userinfo", obj["userinfo_endpoint"]?.jsonPrimitive?.content)
        assertEquals(
            "public",
            obj["subject_types_supported"]
                ?.jsonArray
                ?.get(0)
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(2, obj["id_token_signing_alg_values_supported"]?.jsonArray?.size)
        assertEquals(
            "ES256",
            obj["id_token_signing_alg_values_supported"]
                ?.jsonArray
                ?.get(0)
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(4, obj["claims_supported"]?.jsonArray?.size)
        assertEquals("false", obj["claims_parameter_supported"]?.jsonPrimitive?.content)
        assertEquals("true", obj["request_parameter_supported"]?.jsonPrimitive?.content)
        assertEquals("false", obj["request_uri_parameter_supported"]?.jsonPrimitive?.content)
    }

    @Test
    fun deserializesOidcFields() {
        val input =
            """
            {
                "issuer": "https://auth.example.com",
                "token_endpoint": "https://auth.example.com/token",
                "userinfo_endpoint": "https://auth.example.com/userinfo",
                "subject_types_supported": ["public", "pairwise"],
                "id_token_signing_alg_values_supported": ["ES256"],
                "claims_supported": ["sub", "name", "email"],
                "claims_parameter_supported": true,
                "request_parameter_supported": false,
                "request_uri_parameter_supported": true
            }
            """.trimIndent()

        val metadata = json.decodeFromString(AuthorizationServerMetadata.serializer(), input)

        assertEquals("https://auth.example.com/userinfo", metadata.userinfoEndpoint)
        assertEquals(listOf("public", "pairwise"), metadata.subjectTypesSupported)
        assertEquals(listOf("ES256"), metadata.idTokenSigningAlgValuesSupported)
        assertEquals(listOf("sub", "name", "email"), metadata.claimsSupported)
        assertEquals(true, metadata.claimsParameterSupported)
        assertEquals(false, metadata.requestParameterSupported)
        assertEquals(true, metadata.requestUriParameterSupported)
    }

    @Test
    fun oidcFieldsAreNullWhenAbsent() {
        val input =
            """
            {
                "issuer": "https://auth.example.com",
                "token_endpoint": "https://auth.example.com/token"
            }
            """.trimIndent()

        val metadata = json.decodeFromString(AuthorizationServerMetadata.serializer(), input)

        assertNull(metadata.userinfoEndpoint)
        assertNull(metadata.subjectTypesSupported)
        assertNull(metadata.idTokenSigningAlgValuesSupported)
        assertNull(metadata.claimsSupported)
        assertNull(metadata.claimsParameterSupported)
        assertNull(metadata.requestParameterSupported)
        assertNull(metadata.requestUriParameterSupported)
    }

    @Test
    fun nullOidcFieldsNotSerializedInOutput() {
        val metadata =
            AuthorizationServerMetadata(
                issuer = "https://auth.example.com",
                tokenEndpoint = "https://auth.example.com/token",
            )

        val serialized = json.encodeToString(AuthorizationServerMetadata.serializer(), metadata)
        val obj = json.parseToJsonElement(serialized).jsonObject

        assertTrue("userinfo_endpoint" !in obj)
        assertTrue("subject_types_supported" !in obj)
        assertTrue("id_token_signing_alg_values_supported" !in obj)
        assertTrue("claims_supported" !in obj)
    }

    @Test
    fun oidcFieldsSurviveRoundTrip() {
        val original =
            AuthorizationServerMetadata(
                issuer = "https://auth.example.com",
                tokenEndpoint = "https://auth.example.com/token",
                jwksUri = "https://auth.example.com/.well-known/jwks.json",
                userinfoEndpoint = "https://auth.example.com/userinfo",
                subjectTypesSupported = listOf("public"),
                idTokenSigningAlgValuesSupported = listOf("ES256", "RS256"),
                claimsSupported = listOf("sub", "name", "email", "email_verified", "phone_number"),
                claimsParameterSupported = false,
                requestParameterSupported = true,
                requestUriParameterSupported = false,
                scopesSupported = listOf("openid", "profile", "email", "phone", "address"),
            )

        val serialized = json.encodeToString(AuthorizationServerMetadata.serializer(), original)
        val deserialized = json.decodeFromString(AuthorizationServerMetadata.serializer(), serialized)

        assertEquals(original.issuer, deserialized.issuer)
        assertEquals(original.tokenEndpoint, deserialized.tokenEndpoint)
        assertEquals(original.jwksUri, deserialized.jwksUri)
        assertEquals(original.userinfoEndpoint, deserialized.userinfoEndpoint)
        assertEquals(original.subjectTypesSupported, deserialized.subjectTypesSupported)
        assertEquals(original.idTokenSigningAlgValuesSupported, deserialized.idTokenSigningAlgValuesSupported)
        assertEquals(original.claimsSupported, deserialized.claimsSupported)
        assertEquals(original.claimsParameterSupported, deserialized.claimsParameterSupported)
        assertEquals(original.requestParameterSupported, deserialized.requestParameterSupported)
        assertEquals(original.requestUriParameterSupported, deserialized.requestUriParameterSupported)
        assertEquals(original.scopesSupported, deserialized.scopesSupported)
    }

    @Test
    fun unknownFieldsCapturedInAdditionalMetadata() {
        val input =
            """
            {
                "issuer": "https://auth.example.com",
                "token_endpoint": "https://auth.example.com/token",
                "userinfo_endpoint": "https://auth.example.com/userinfo",
                "custom_extension_field": "custom_value",
                "another_extension": true
            }
            """.trimIndent()

        val metadata = json.decodeFromString(AuthorizationServerMetadata.serializer(), input)

        assertEquals("https://auth.example.com/userinfo", metadata.userinfoEndpoint)
        assertNotNull(metadata.additionalMetadata["custom_extension_field"])
        assertEquals("custom_value", (metadata.additionalMetadata["custom_extension_field"] as JsonPrimitive).content)
        // userinfo_endpoint should NOT be in additionalMetadata
        assertNull(metadata.additionalMetadata["userinfo_endpoint"])
    }
}
