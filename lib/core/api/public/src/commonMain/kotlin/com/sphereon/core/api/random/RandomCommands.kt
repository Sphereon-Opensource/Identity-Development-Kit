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

package com.sphereon.core.api.random

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ByteArrayResult
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.session.Command
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/** Default token entropy in bytes (256 bits) — OAuth2/OIDC recommended minimum for opaque identifiers. */
public const val DEFAULT_TOKEN_BYTES: Int = 32

/**
 * Arguments for [GenerateTokenCommand].
 *
 * @property lengthBytes number of random bytes to draw before encoding (default [DEFAULT_TOKEN_BYTES] = 32)
 * @property encoding output encoding (default [Encoding.BASE64URL] — canonical for OAuth2/OIDC opaque tokens)
 */
@JsExportCompat
@Serializable
data class GenerateTokenArgs(
    val lengthBytes: Int = DEFAULT_TOKEN_BYTES,
    val encoding: Encoding = Encoding.BASE64URL,
)

/**
 * Arguments for [NextBytesCommand].
 *
 * @property length number of random bytes to produce
 */
@JsExportCompat
@Serializable
data class NextBytesArgs(
    val length: Int,
)

/**
 * Generate a cryptographically-random token, encoded per [GenerateTokenArgs.encoding].
 *
 * Implementations MUST draw from a platform CSPRNG (never `kotlin.random.Random`) since
 * output is exposed in user-facing token formats (authorization codes, access tokens,
 * nonces, PAR request URIs, credential identifiers).
 */
@JsExportCompat
interface GenerateTokenCommand : Command<GenerateTokenArgs, StringResult, IdkError> {
    override val id: String get() = COMMAND_ID

    public companion object {
        public const val COMMAND_ID: String = "core.random.generate-token"
    }
}

/**
 * Generate [NextBytesArgs.length] cryptographically-random bytes, unencoded.
 *
 * Used when a caller needs raw entropy — e.g. as HMAC key material, PKCE verifier input
 * prior to hashing, or challenge bytes that will be transformed by a domain-specific
 * encoder. For string-shaped tokens prefer [GenerateTokenCommand].
 */
@JsExportCompat
interface NextBytesCommand : Command<NextBytesArgs, ByteArrayResult, IdkError> {
    override val id: String get() = COMMAND_ID

    public companion object {
        public const val COMMAND_ID: String = "core.random.next-bytes"
    }
}
