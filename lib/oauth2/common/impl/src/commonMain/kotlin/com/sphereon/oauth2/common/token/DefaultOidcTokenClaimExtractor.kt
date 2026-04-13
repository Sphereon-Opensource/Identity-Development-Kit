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

package com.sphereon.oauth2.common.token

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsUtils
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Default implementation of [OidcTokenClaimExtractor].
 *
 * Decodes the JWT payload using [JwsUtils.decodeBase64UrlToJson] and
 * navigates nested JSON structures for claim path resolution.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<OidcTokenClaimExtractor>())
class DefaultOidcTokenClaimExtractor : OidcTokenClaimExtractor {
    override fun extractAllClaims(jwt: String): IdkResult<Map<String, JsonElement>, IdkError> {
        val payload =
            decodePayload(jwt)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed JWT: expected 3 dot-separated parts"))
        return Ok(payload.toMap())
    }

    override fun extractClaim(
        jwt: String,
        claimPath: List<String>,
    ): IdkResult<JsonElement?, IdkError> {
        val payload =
            decodePayload(jwt)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed JWT: expected 3 dot-separated parts"))

        var current: JsonElement = payload
        for (segment in claimPath) {
            val obj = current as? JsonObject ?: return Ok(null)
            current = obj[segment] ?: return Ok(null)
        }
        return Ok(current)
    }

    private fun decodePayload(jwt: String): JsonObject? {
        val parts = jwt.split(".")
        if (parts.size < 3) {
            return null
        }
        return try {
            JwsUtils.decodeBase64UrlToJson(parts[1])
        } catch (_: Exception) {
            // Ignored: JWT payload base64url decode failed
            null
        }
    }
}
