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

package com.sphereon.oauth2.server.authorization.iae

import com.sphereon.oauth2.server.authorization.model.IaeAuthorizationCodeResponse
import com.sphereon.oauth2.server.authorization.model.IaeErrorResponse
import com.sphereon.oauth2.server.authorization.model.IaeErrors
import com.sphereon.oauth2.server.authorization.model.IaeInteractionRequiredResponse
import com.sphereon.oauth2.server.authorization.model.IaeInteractionTypes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies the wire format for all IAE response types.
 *
 * OID4VCI 1.1 Section 6 defines the exact JSON field names. These tests guard against
 * accidental drift between Kotlin property names and the serialized wire names.
 */
class IaeModelSerializationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            // Default values (e.g. status fields) must be encoded for spec-compliant wire format
            encodeDefaults = true
        }

    // -------------------------------------------------------------------------
    // 1. IaeInteractionRequiredResponse — VP presentation variant
    // -------------------------------------------------------------------------

    @Test
    fun serializeInteractionRequiredVpPresentation() {
        val vpRequest =
            JsonObject(
                mapOf(
                    "response_type" to JsonPrimitive("vp_token"),
                    "response_mode" to JsonPrimitive("iae_post"),
                    "nonce" to JsonPrimitive("test-nonce-abc"),
                ),
            )
        val response =
            IaeInteractionRequiredResponse(
                type = IaeInteractionTypes.OPENID4VP_PRESENTATION,
                authSession = "auth-session-token-xyz",
                openid4vpRequest = vpRequest,
            )

        val encoded = json.encodeToString(IaeInteractionRequiredResponse.serializer(), response)
        val decoded = json.parseToJsonElement(encoded).jsonObject

        assertEquals(
            "require_interaction",
            decoded["status"]?.jsonPrimitive?.content,
            "status field must be 'require_interaction'",
        )
        assertEquals(
            IaeInteractionTypes.OPENID4VP_PRESENTATION,
            decoded["type"]?.jsonPrimitive?.content,
            "type field must match OPENID4VP_PRESENTATION URN",
        )
        assertEquals(
            "auth-session-token-xyz",
            decoded["auth_session"]?.jsonPrimitive?.content,
            "auth_session must use snake_case wire name",
        )
        val openid4vpRequestElem = decoded["openid4vp_request"]
        assertTrue(
            openid4vpRequestElem != null && openid4vpRequestElem != JsonNull,
            "openid4vp_request must be present and non-null for VP presentation",
        )
        val requestUriElem = decoded["request_uri"]
        assertTrue(
            requestUriElem == null || requestUriElem == JsonNull,
            "request_uri must be absent or null for VP presentation",
        )
        val expiresInElem = decoded["expires_in"]
        assertTrue(
            expiresInElem == null || expiresInElem == JsonNull,
            "expires_in must be absent or null for VP presentation",
        )

        // Check nested VP request fields
        val vpObj = decoded["openid4vp_request"]?.jsonObject
        assertEquals("vp_token", vpObj?.get("response_type")?.jsonPrimitive?.content)
        assertEquals("iae_post", vpObj?.get("response_mode")?.jsonPrimitive?.content)
        assertEquals("test-nonce-abc", vpObj?.get("nonce")?.jsonPrimitive?.content)
    }

    // -------------------------------------------------------------------------
    // 2. IaeInteractionRequiredResponse — redirect_to_web variant
    // -------------------------------------------------------------------------

    @Test
    fun serializeInteractionRequiredRedirectToWeb() {
        val response =
            IaeInteractionRequiredResponse(
                type = IaeInteractionTypes.REDIRECT_TO_WEB,
                authSession = "auth-session-redirect-999",
                requestUri = "urn:ietf:params:oauth:request_uri:abc123",
                expiresIn = 60,
            )

        val encoded = json.encodeToString(IaeInteractionRequiredResponse.serializer(), response)
        val decoded = json.parseToJsonElement(encoded).jsonObject

        assertEquals(
            "require_interaction",
            decoded["status"]?.jsonPrimitive?.content,
            "status must be 'require_interaction'",
        )
        assertEquals(
            IaeInteractionTypes.REDIRECT_TO_WEB,
            decoded["type"]?.jsonPrimitive?.content,
            "type must match REDIRECT_TO_WEB URN",
        )
        assertEquals(
            "auth-session-redirect-999",
            decoded["auth_session"]?.jsonPrimitive?.content,
            "auth_session wire name must be snake_case",
        )
        assertEquals(
            "urn:ietf:params:oauth:request_uri:abc123",
            decoded["request_uri"]?.jsonPrimitive?.content,
            "request_uri must be present for redirect_to_web",
        )
        assertEquals(
            "60",
            decoded["expires_in"]?.jsonPrimitive?.content,
            "expires_in must be present and equal 60",
        )
        val openid4vpRequestElem2 = decoded["openid4vp_request"]
        assertTrue(
            openid4vpRequestElem2 == null || openid4vpRequestElem2 == JsonNull,
            "openid4vp_request must be absent or null for redirect_to_web",
        )
    }

    // -------------------------------------------------------------------------
    // 3. IaeAuthorizationCodeResponse
    // -------------------------------------------------------------------------

    @Test
    fun serializeAuthorizationCodeResponse() {
        val response = IaeAuthorizationCodeResponse(code = "SplxlOBeZQQYbYS6WxSbIA")

        val encoded = json.encodeToString(IaeAuthorizationCodeResponse.serializer(), response)
        val decoded = json.parseToJsonElement(encoded).jsonObject

        assertEquals(
            "ok",
            decoded["status"]?.jsonPrimitive?.content,
            "status must be 'ok'",
        )
        assertEquals(
            "SplxlOBeZQQYbYS6WxSbIA",
            decoded["code"]?.jsonPrimitive?.content,
            "code field must be present",
        )
    }

    // -------------------------------------------------------------------------
    // 4. IaeErrorResponse — missing_interaction_type
    // -------------------------------------------------------------------------

    @Test
    fun serializeErrorResponseMissingInteractionType() {
        val response =
            IaeErrorResponse(
                error = IaeErrors.MISSING_INTERACTION_TYPE,
                errorDescription = "interaction_types_supported must not be empty",
            )

        val encoded = json.encodeToString(IaeErrorResponse.serializer(), response)
        val decoded = json.parseToJsonElement(encoded).jsonObject

        assertEquals(
            IaeErrors.MISSING_INTERACTION_TYPE,
            decoded["error"]?.jsonPrimitive?.content,
            "error field must match MISSING_INTERACTION_TYPE constant",
        )
        assertEquals(
            "interaction_types_supported must not be empty",
            decoded["error_description"]?.jsonPrimitive?.content,
            "error_description must use snake_case wire name",
        )
    }

    // -------------------------------------------------------------------------
    // Round-trip: deserialize what we serialized
    // -------------------------------------------------------------------------

    @Test
    fun roundTripAuthorizationCodeResponse() {
        val original = IaeAuthorizationCodeResponse(code = "round-trip-code-42")
        val encoded = json.encodeToString(IaeAuthorizationCodeResponse.serializer(), original)
        val decoded = json.decodeFromString(IaeAuthorizationCodeResponse.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun roundTripErrorResponse() {
        val original =
            IaeErrorResponse(
                error = IaeErrors.ACCESS_DENIED,
                errorDescription = "Consent denied",
            )
        val encoded = json.encodeToString(IaeErrorResponse.serializer(), original)
        val decoded = json.decodeFromString(IaeErrorResponse.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
