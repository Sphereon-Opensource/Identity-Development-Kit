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
import kotlin.test.assertNull

/**
 * WP2 Task 2.9 — RFC 6749 §5.2 error response conformance pass.
 *
 * Every OAuth2 error rendered by the AS must:
 * - Be `application/json`.
 * - Carry `Cache-Control: no-store` AND `Pragma: no-cache`.
 * - Have a JSON body with an `error` field (and optional `error_description`, `error_uri`).
 * - Map to the RFC-specified HTTP status.
 */
class TokenEndpointErrorShapeTest {
    private val json = Json { ignoreUnknownKeys = true }

    /** (errorCode → expectedHttpStatus). Derived from RFC 6749 §5.2 + RFC 8628 §3.5. */
    private val rfcStatusMap =
        listOf(
            "invalid_request" to 400,
            "invalid_client" to 401,
            "invalid_grant" to 400,
            "unauthorized_client" to 401,
            "unsupported_grant_type" to 400,
            "invalid_scope" to 400,
            // RFC 8628 §3.5 device-code grant token-endpoint errors
            "authorization_pending" to 400,
            "slow_down" to 400,
            "expired_token" to 400,
            "access_denied" to 400,
        )

    @Test
    fun allStandardErrorCodes_mapToRfcStatusWithConformantShape() {
        for ((code, expectedStatus) in rfcStatusMap) {
            val err =
                IdkError(
                    code = code,
                    message = IdkError.Message(i18nKey = "test.$code", defaultMessage = "fake $code"),
                )
            val response = mapOAuth2ErrorToResponse(err, json)

            assertEquals(expectedStatus, response.statusCode, "$code must map to HTTP $expectedStatus")
            assertEquals("application/json", response.headers["Content-Type"], "$code response must be JSON")
            assertEquals("no-store", response.headers["Cache-Control"], "$code must be uncached (Cache-Control)")
            assertEquals("no-cache", response.headers["Pragma"], "$code must be uncached (Pragma)")

            val body = json.parseToJsonElement(response.body!!).jsonObject
            assertEquals(code, body["error"]?.jsonPrimitive?.content, "$code body must carry error code")
            assertEquals("fake $code", body["error_description"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun errorResponse_withoutDescription_omitsErrorDescriptionField() {
        val response = oauth2ErrorResponse(400, "invalid_request", errorDescription = null, jsonFormat = json)
        val body = json.parseToJsonElement(response.body!!).jsonObject
        assertEquals("invalid_request", body["error"]?.jsonPrimitive?.content)
        assertNull(body["error_description"], "error_description must be omitted, not nulled, when absent")
    }

    @Test
    fun unknownErrorCode_mapsToServerError500() {
        val err =
            IdkError(
                code = "mysterious_unknown_code",
                message = IdkError.Message(i18nKey = "unknown", defaultMessage = "oops"),
            )
        val response = mapOAuth2ErrorToResponse(err, json)
        assertEquals(500, response.statusCode)
        val body = json.parseToJsonElement(response.body!!).jsonObject
        assertEquals("server_error", body["error"]?.jsonPrimitive?.content)
    }
}
