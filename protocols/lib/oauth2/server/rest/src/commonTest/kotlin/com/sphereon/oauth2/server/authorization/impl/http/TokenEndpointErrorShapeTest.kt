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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenEndpointErrorShapeTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val clientAndProtocolErrors =
        listOf(
            "invalid_request" to "invalid_request",
            "unauthorized_client" to "unauthorized_client",
            "invalid_grant" to "invalid_grant",
            "unsupported_grant_type" to "unsupported_grant_type",
            "invalid_scope" to "invalid_scope",
            "invalid_target" to "invalid_target",
            "access_denied" to "access_denied",
            "unsupported_response_type" to "unsupported_response_type",
            "request_not_supported" to "request_not_supported",
            "request_uri_not_supported" to "request_uri_not_supported",
            "invalid_request_object" to "invalid_request_object",
            "invalid_request_uri" to "invalid_request_uri",
            "invalid_authorization_details" to "invalid_authorization_details",
            "insufficient_user_authentication" to "insufficient_user_authentication",
            "interaction_required" to "interaction_required",
            "invalid_dpop_proof" to "invalid_dpop_proof",
            "use_dpop_nonce" to "use_dpop_nonce",
            "invalid_client_attestation" to "invalid_client_attestation",
            "use_attestation_challenge" to "use_attestation_challenge",
            "use_fresh_attestation" to "use_fresh_attestation",
            "authorization_pending" to "authorization_pending",
            "slow_down" to "slow_down",
            "expired_token" to "expired_token",
            "session_not_found" to "invalid_request",
            "client_not_found" to "invalid_client",
            "UNAUTHORIZED" to "invalid_client",
            "FORBIDDEN" to "invalid_grant",
            "ILLEGAL_ARGUMENT_ERROR" to "invalid_request",
            "COMMAND_ARG_NOT_SUPPORTED_ERROR" to "invalid_request",
            "COMMAND_DISABLED" to "temporarily_unavailable",
            "TIMEOUT" to "temporarily_unavailable",
            "SERVICE_UNAVAILABLE" to "temporarily_unavailable",
            "COMMAND_NOT_AUTHORIZED" to "invalid_grant",
        )

    @Test
    fun everyKnownClientOrProtocolErrorMapsToItsWireCodeWithoutLeakingDetails() {
        for ((idkCode, wireCode) in clientAndProtocolErrors) {
            val response = mapOAuth2ErrorToResponse(error(idkCode), json, endpoint = OAuth2ErrorEndpoint.TOKEN)
            val body = json.parseToJsonElement(response.body!!).jsonObject

            val expectedStatus = if (wireCode == "temporarily_unavailable") 503 else 400
            assertEquals(expectedStatus, response.statusCode, "$idkCode status")
            assertEquals(wireCode, body["error"]?.jsonPrimitive?.content, "$idkCode wire error")
            if (wireCode == "temporarily_unavailable") {
                assertEquals("The authorization server is temporarily unavailable.", body["error_description"]?.jsonPrimitive?.content)
            } else {
                assertNull(body["error_description"], "$idkCode must not expose the internal message")
            }
            assertEquals("application/json", response.headers["Content-Type"])
            assertEquals("no-store", response.headers["Cache-Control"])
            assertEquals("no-cache", response.headers["Pragma"])
        }
    }

    @Test
    fun invalidClientStatusDependsOnEndpointAndHttpAuthenticationAttempt() {
        val tokenWithoutHttpAuth = mapOAuth2ErrorToResponse(error("invalid_client"), json, endpoint = OAuth2ErrorEndpoint.TOKEN)
        val tokenWithBasic =
            mapOAuth2ErrorToResponse(
                error("invalid_client"),
                json,
                endpoint = OAuth2ErrorEndpoint.TOKEN,
                httpAuthenticationAttempted = true,
            )
        val introspection = mapOAuth2ErrorToResponse(error("invalid_client"), json, endpoint = OAuth2ErrorEndpoint.INTROSPECTION)
        val revocation = mapOAuth2ErrorToResponse(error("invalid_client"), json, endpoint = OAuth2ErrorEndpoint.REVOCATION)

        assertEquals(400, tokenWithoutHttpAuth.statusCode)
        assertEquals(401, tokenWithBasic.statusCode)
        assertEquals(401, introspection.statusCode)
        assertEquals(401, revocation.statusCode)
    }

    @Test
    fun coreAuthorizationRefusalsAreEndpointAwareAndDeviceAccessDeniedStaysLiteral() {
        val tokenRefusal = mapOAuth2ErrorToResponse(error("COMMAND_NOT_AUTHORIZED"), json, endpoint = OAuth2ErrorEndpoint.TOKEN)
        val otherRefusal = mapOAuth2ErrorToResponse(error("FORBIDDEN"), json, endpoint = OAuth2ErrorEndpoint.OTHER)
        val devicePollRefusal = mapOAuth2ErrorToResponse(error("access_denied"), json, endpoint = OAuth2ErrorEndpoint.TOKEN)

        assertEquals("invalid_grant", errorCode(tokenRefusal))
        assertEquals("access_denied", errorCode(otherRefusal))
        assertEquals("access_denied", errorCode(devicePollRefusal))
    }

    @Test
    fun genuineServerFailuresUseSanitized500And503Responses() {
        val secretMessage = "private key material and internal exception text"
        val serverError = mapOAuth2ErrorToResponse(error("server_error", secretMessage), json)
        val storageError = mapOAuth2ErrorToResponse(error("storage_error", secretMessage), json)
        val unknownError = mapOAuth2ErrorToResponse(error("unknown_internal_code", secretMessage), json)
        val unavailable = mapOAuth2ErrorToResponse(error("temporarily_unavailable", secretMessage), json)

        for (response in listOf(serverError, storageError, unknownError)) {
            assertEquals(500, response.statusCode)
            assertEquals("server_error", errorCode(response))
            assertTrue(response.body!!.contains("An unexpected error occurred."))
            assertFalse(response.body!!.contains(secretMessage))
        }
        assertEquals(503, unavailable.statusCode)
        assertEquals("temporarily_unavailable", errorCode(unavailable))
        assertTrue(unavailable.body!!.contains("temporarily unavailable"))
        assertFalse(unavailable.body!!.contains(secretMessage))
    }

    @Test
    fun nonceAndAttestationChallengesSurviveErrorMappingAndBasicChallenge() {
        val dpopError = error("use_dpop_nonce", meta = mapOf("dpop_nonce" to "nonce-123"))
        val dpopResponse = mapOAuth2ErrorToResponse(dpopError, json, endpoint = OAuth2ErrorEndpoint.TOKEN)
        assertEquals("nonce-123", dpopResponse.headers["DPoP-Nonce"])

        val attestationError = error("use_attestation_challenge", meta = mapOf("attestation_challenge" to "challenge-456"))
        val attestationResponse = mapOAuth2ErrorToResponse(attestationError, json, endpoint = OAuth2ErrorEndpoint.TOKEN)
        assertEquals(400, attestationResponse.statusCode)
        assertEquals("challenge-456", attestationResponse.headers["OAuth-Client-Attestation-Challenge"])

        val combined =
            com.sphereon.core.api.http.GenericHttpResponse(
                statusCode = 401,
                headers =
                    mapOf(
                        "DPoP-Nonce" to "nonce-123",
                        "OAuth-Client-Attestation-Challenge" to "challenge-456",
                    ),
                body = "{}",
            ).withWwwAuthenticateIfBasicInternal(basicWasAttempted = true)
        assertEquals("nonce-123", combined.headers["DPoP-Nonce"])
        assertEquals("challenge-456", combined.headers["OAuth-Client-Attestation-Challenge"])
        assertEquals("Basic realm=\"oauth2\"", combined.headers["WWW-Authenticate"])
    }

    @Test
    fun withWwwAuthenticatePreservesAnExistingAuthenticationChallenge() {
        val response =
            com.sphereon.core.api.http.GenericHttpResponse(
                statusCode = 401,
                headers = mapOf("www-authenticate" to "DPoP error=\"invalid_token\"", "X-Test" to "kept"),
                body = "{}",
            )

        val challenged = response.withWwwAuthenticateIfBasicInternal(basicWasAttempted = true)

        assertEquals("DPoP error=\"invalid_token\"", challenged.headers["www-authenticate"])
        assertEquals("kept", challenged.headers["X-Test"])
    }

    private fun error(
        code: String,
        message: String = "internal detail that must never reach the response",
        meta: Map<String, Any?> = emptyMap(),
    ): IdkError =
        IdkError(
            code = code,
            message = IdkError.Message(i18nKey = "test.$code", defaultMessage = message),
            meta = meta,
        )

    private fun errorCode(response: com.sphereon.core.api.http.GenericHttpResponse): String? =
        json.parseToJsonElement(response.body!!).jsonObject["error"]?.jsonPrimitive?.content
}
