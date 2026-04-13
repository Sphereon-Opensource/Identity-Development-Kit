/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.core.defaults.context

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Multiplatform JWT claims parser.
 *
 * This parser only decodes JWT claims - it does NOT validate signatures.
 * For signature validation, use a proper JWT validation library.
 *
 * **Usage:**
 * ```kotlin
 * val claims = JwtClaimsParser.parseClaimsOrNull(jwt)
 * if (claims != null) {
 *     val subject = claims["sub"]?.jsonPrimitive?.content
 *     val tenantId = claims["tenant_id"]?.jsonPrimitive?.content
 * }
 * ```
 *
 * **Security Note:**
 * This parser is intended for claim extraction after token validation has occurred
 * at the authentication layer (e.g., OAuth2 provider, API gateway).
 */
object JwtClaimsParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses JWT claims from a JWT token string.
     *
     * @param jwt The JWT token string (header.payload.signature)
     * @return Parsed claims map or null if parsing fails
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun parseClaimsOrNull(jwt: String): Map<String, JsonElement>? {
        return try {
            val parts = jwt.split(".")
            if (parts.size != 3) return null

            val payloadBytes = Base64.UrlSafe.decode(padBase64(parts[1]))
            val payloadJson = payloadBytes.decodeToString()
            json.decodeFromString<JsonObject>(payloadJson)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Creates a [JwtClaimsInput] from a JWT token string.
     *
     * @param jwt The JWT token string
     * @return JwtClaimsInput with parsed claims, or null if parsing fails
     */
    fun toJwtClaimsInput(jwt: String): JwtClaimsInput? {
        val claims = parseClaimsOrNull(jwt) ?: return null
        return JwtClaimsInput(claims = claims, rawToken = jwt)
    }

    /**
     * Pads Base64 URL-safe encoded string to proper length.
     * JWT tokens often omit padding characters.
     */
    private fun padBase64(input: String): String {
        val remainder = input.length % 4
        return if (remainder == 0) {
            input
        } else {
            input + "=".repeat(4 - remainder)
        }
    }
}
