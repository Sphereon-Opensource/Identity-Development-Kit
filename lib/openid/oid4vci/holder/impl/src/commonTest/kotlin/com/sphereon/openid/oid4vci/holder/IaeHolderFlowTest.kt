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

import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IaeHolderFlowTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseInteractionRequiredWithVpPresentation() {
        val responseJson =
            """
            {
              "status": "require_interaction",
              "type": "urn:openid:dcp:iae:openid4vp_presentation",
              "auth_session": "wxroVrBY2MCq4dDNGXACS",
              "openid4vp_request": {
                "response_type": "vp_token",
                "response_mode": "iae_post",
                "nonce": "n-0S6_WzA2Mj"
              }
            }
            """.trimIndent()
        val body = json.parseToJsonElement(responseJson).jsonObject
        assertEquals("require_interaction", body["status"]?.jsonPrimitive?.content)
        assertEquals("urn:openid:dcp:iae:openid4vp_presentation", body["type"]?.jsonPrimitive?.content)
        assertNotNull(body["openid4vp_request"])
        assertEquals(
            "iae_post",
            body["openid4vp_request"]
                ?.jsonObject
                ?.get("response_mode")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun parseInteractionRequiredWithRedirectToWeb() {
        val responseJson =
            """
            {
              "status": "require_interaction",
              "type": "urn:openid:dcp:iae:redirect_to_web",
              "request_uri": "urn:ietf:params:oauth:request_uri:6esc_11ACC5bwc014ltc14eY22c",
              "expires_in": 60
            }
            """.trimIndent()
        val body = json.parseToJsonElement(responseJson).jsonObject
        assertEquals("urn:openid:dcp:iae:redirect_to_web", body["type"]?.jsonPrimitive?.content)
        assertNotNull(body["request_uri"])
    }

    @Test
    fun parseAuthorizationCodeResponse() {
        val responseJson = """{"status": "ok", "code": "uY29tL2F1dGhlbnRpY"}"""
        val body = json.parseToJsonElement(responseJson).jsonObject
        assertEquals("ok", body["status"]?.jsonPrimitive?.content)
        assertEquals("uY29tL2F1dGhlbnRpY", body["code"]?.jsonPrimitive?.content)
    }

    @Test
    fun parseErrorResponse() {
        val responseJson =
            """
            {"error": "missing_interaction_type", "error_description": "missing urn:openid:dcp:iae:openid4vp_presentation"}
            """.trimIndent()
        val body = json.parseToJsonElement(responseJson).jsonObject
        assertEquals("missing_interaction_type", body["error"]?.jsonPrimitive?.content)
    }

    // ============================================================================
    // InitiateIaeCommand — form parameter encoding tests
    // ============================================================================

    @Test
    fun initiateIaeInteractionTypesSupportedAreCommaJoined() {
        val types =
            listOf(
                "urn:openid:dcp:iae:openid4vp_presentation",
                "urn:openid:dcp:iae:redirect_to_web",
            )
        val params =
            Parameters.build {
                append("response_type", "code")
                append("client_id", "wallet-client")
                append("redirect_uri", "https://wallet.example.com/cb")
                append("interaction_types_supported", types.joinToString(","))
            }
        assertEquals("code", params["response_type"])
        assertEquals("wallet-client", params["client_id"])
        assertEquals("https://wallet.example.com/cb", params["redirect_uri"])
        assertEquals(
            "urn:openid:dcp:iae:openid4vp_presentation,urn:openid:dcp:iae:redirect_to_web",
            params["interaction_types_supported"],
        )
    }

    @Test
    fun initiateIaeOptionalFieldsOmittedWhenNull() {
        val params =
            Parameters.build {
                append("response_type", "code")
                append("client_id", "wallet-client")
                append("redirect_uri", "https://wallet.example.com/cb")
                append("interaction_types_supported", "urn:openid:dcp:iae:openid4vp_presentation")
                // scope, code_challenge, code_challenge_method, authorization_details intentionally omitted
            }
        assertTrue(params["scope"] == null)
        assertTrue(params["code_challenge"] == null)
        assertTrue(params["code_challenge_method"] == null)
        assertTrue(params["authorization_details"] == null)
    }

    @Test
    fun initiateIaePkceFieldsIncludedWhenRedirectToWebSupported() {
        val params =
            Parameters.build {
                append("response_type", "code")
                append("client_id", "wallet-client")
                append("redirect_uri", "https://wallet.example.com/cb")
                append("interaction_types_supported", "urn:openid:dcp:iae:redirect_to_web")
                append("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                append("code_challenge_method", "S256")
            }
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", params["code_challenge"])
        assertEquals("S256", params["code_challenge_method"])
    }

    @Test
    fun initiateIaeAuthorizationDetailsSerializedAsJsonArrayString() {
        val authDetails =
            listOf(
                buildJsonObject {
                    put("type", JsonPrimitive("openid_credential"))
                    put("credential_configuration_id", JsonPrimitive("UniversityDegreeCredential"))
                },
            )
        val params =
            Parameters.build {
                append("authorization_details", "[${authDetails.joinToString(",")}]")
            }
        val value = params["authorization_details"]
        assertNotNull(value)
        assertTrue(value.startsWith("["))
        assertTrue(value.contains("openid_credential"))
    }

    @Test
    fun initiateIaeParseInteractionRequiredResponse() {
        val responseJson =
            """
            {
              "status": "require_interaction",
              "type": "urn:openid:dcp:iae:openid4vp_presentation",
              "auth_session": "initiateSession123",
              "openid4vp_request": {
                "response_type": "vp_token",
                "response_mode": "iae_post",
                "nonce": "abc-nonce"
              }
            }
            """.trimIndent()
        val body = json.parseToJsonElement(responseJson).jsonObject
        assertEquals("require_interaction", body["status"]?.jsonPrimitive?.content)
        assertEquals("urn:openid:dcp:iae:openid4vp_presentation", body["type"]?.jsonPrimitive?.content)
        assertEquals("initiateSession123", body["auth_session"]?.jsonPrimitive?.content)
        assertNotNull(body["openid4vp_request"])
    }

    @Test
    fun initiateIaeParseAuthorizationCodeResponse() {
        val responseJson = """{"status": "ok", "code": "initiate-auth-code-xyz"}"""
        val body = json.parseToJsonElement(responseJson).jsonObject
        assertEquals("ok", body["status"]?.jsonPrimitive?.content)
        assertEquals("initiate-auth-code-xyz", body["code"]?.jsonPrimitive?.content)
    }

    @Test
    fun initiateIaeParseErrorResponse() {
        val responseJson =
            """
            {"error": "invalid_client", "error_description": "Unknown client_id"}
            """.trimIndent()
        val body = json.parseToJsonElement(responseJson).jsonObject
        assertEquals("invalid_client", body["error"]?.jsonPrimitive?.content)
        assertEquals("Unknown client_id", body["error_description"]?.jsonPrimitive?.content)
    }
}
