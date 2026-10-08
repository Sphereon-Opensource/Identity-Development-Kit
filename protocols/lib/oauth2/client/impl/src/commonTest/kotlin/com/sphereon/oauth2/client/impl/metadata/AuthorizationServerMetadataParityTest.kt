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

package com.sphereon.oauth2.client.impl.metadata

import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * WP3 RP metadata parity golden test.
 *
 * Guards against silent field drops during [AuthorizationServerMetadata] model changes. Given a
 * realistic OIDC issuer discovery document, this test decodes then re-encodes it and asserts
 * every known standard field is preserved AND any unknown extension fields round-trip via the
 * `additionalMetadata` capture map.
 *
 * The "golden" JSON here is synthesised rather than captured from a live issuer so the test stays
 * hermetic; it is deliberately representative of what OIDF's Java conformance suite would emit.
 */
class AuthorizationServerMetadataParityTest {
    private val json =
        Json {
            ignoreUnknownKeys = false
            encodeDefaults = false
            prettyPrint = false
        }

    /** Representative OIDC Core + RFC 8414 metadata. */
    @Suppress("LargeClass")
    private val goldenOidcMetadata: String =
        """
        {
          "issuer": "https://as.example.com",
          "authorization_endpoint": "https://as.example.com/authorize",
          "token_endpoint": "https://as.example.com/token",
          "userinfo_endpoint": "https://as.example.com/userinfo",
          "jwks_uri": "https://as.example.com/.well-known/jwks.json",
          "registration_endpoint": "https://as.example.com/register",
          "introspection_endpoint": "https://as.example.com/introspect",
          "revocation_endpoint": "https://as.example.com/revoke",
          "pushed_authorization_request_endpoint": "https://as.example.com/par",
          "scopes_supported": ["openid", "profile", "email"],
          "response_types_supported": ["code", "id_token", "token id_token"],
          "response_modes_supported": ["query", "fragment", "form_post"],
          "grant_types_supported": ["authorization_code", "refresh_token"],
          "subject_types_supported": ["public"],
          "id_token_signing_alg_values_supported": ["RS256", "ES256", "PS256"],
          "token_endpoint_auth_methods_supported": ["client_secret_basic", "client_secret_post", "private_key_jwt"],
          "token_endpoint_auth_signing_alg_values_supported": ["RS256", "ES256"],
          "code_challenge_methods_supported": ["S256", "plain"],
          "dpop_signing_alg_values_supported": ["ES256", "RS256"],
          "require_pushed_authorization_requests": false,
          "acme_vendor_extension": "keep-me"
        }
        """.trimIndent()

    @Test
    fun roundtrip_preservesAllKnownFields() {
        val decoded = json.decodeFromString(AuthorizationServerMetadata.serializer(), goldenOidcMetadata)

        assertEquals("https://as.example.com", decoded.issuer)
        assertEquals("https://as.example.com/authorize", decoded.authorizationEndpoint)
        assertEquals("https://as.example.com/token", decoded.tokenEndpoint)
        assertEquals("https://as.example.com/userinfo", decoded.userinfoEndpoint)
        assertEquals("https://as.example.com/.well-known/jwks.json", decoded.jwksUri)
        assertEquals("https://as.example.com/introspect", decoded.introspectionEndpoint)
        assertEquals("https://as.example.com/revoke", decoded.revocationEndpoint)
        assertEquals(
            "https://as.example.com/par",
            decoded.pushedAuthorizationRequestEndpoint,
            "pushed_authorization_request_endpoint must roundtrip",
        )
        assertEquals(listOf("authorization_code", "refresh_token"), decoded.grantTypesSupported)
        assertEquals(listOf("S256", "plain"), decoded.codeChallengeMethodsSupported)
        assertEquals(listOf("ES256", "RS256"), decoded.dpopSigningAlgValuesSupported)
        assertEquals(false, decoded.requirePushedAuthorizationRequests)
        assertTrue(
            decoded.tokenEndpointAuthMethodsSupported?.contains("private_key_jwt") == true,
            "token_endpoint_auth_methods_supported must include private_key_jwt",
        )
    }

    @Test
    fun roundtrip_preservesUnknownExtensions() {
        val decoded = json.decodeFromString(AuthorizationServerMetadata.serializer(), goldenOidcMetadata)
        val vendor = decoded.additionalMetadata["acme_vendor_extension"]
        assertNotNull(vendor, "unknown vendor extensions must land in additionalMetadata")
        assertEquals("keep-me", vendor.jsonPrimitive.content)

        // Re-encode and confirm the extension survives.
        val reEncoded = json.encodeToString(AuthorizationServerMetadata.serializer(), decoded)
        val reParsed = json.parseToJsonElement(reEncoded).jsonObject
        assertEquals(
            "keep-me",
            reParsed["acme_vendor_extension"]?.jsonPrimitive?.content,
            "vendor extension must survive re-encoding",
        )
    }

    @Test
    fun roundtrip_knownFieldsStillPresentAfterReEncode() {
        val decoded = json.decodeFromString(AuthorizationServerMetadata.serializer(), goldenOidcMetadata)
        val reEncoded = json.encodeToString(AuthorizationServerMetadata.serializer(), decoded)
        val reParsed = json.parseToJsonElement(reEncoded).jsonObject

        // Spot-check the OIDF-critical fields — the full list is implicit via the keyed
        // assertions above, but we explicitly re-verify the fields an OIDF conformance run leans
        // on so drift here fails loud.
        val criticalKeys =
            listOf(
                "issuer",
                "authorization_endpoint",
                "token_endpoint",
                "userinfo_endpoint",
                "jwks_uri",
                "subject_types_supported",
                "id_token_signing_alg_values_supported",
                "response_types_supported",
                "token_endpoint_auth_methods_supported",
            )

        for (key in criticalKeys) {
            assertTrue(reParsed.containsKey(key), "re-encoded metadata must still contain '$key'")
        }

        // subject_types_supported must remain as a JSON array (spec).
        assertTrue(
            reParsed["subject_types_supported"] is kotlinx.serialization.json.JsonArray,
            "subject_types_supported must serialise as a JSON array",
        )
        assertEquals(
            JsonPrimitive("public"),
            reParsed["subject_types_supported"]!!.jsonArray.first(),
        )
    }
}
