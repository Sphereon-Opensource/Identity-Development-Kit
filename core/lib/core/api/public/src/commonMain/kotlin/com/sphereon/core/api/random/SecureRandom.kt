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
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ByteArrayResult
import com.sphereon.core.api.service.ServiceFacade
import com.sphereon.core.api.service.StringResult

/**
 * Cryptographically-secure random source for tokens, nonces, identifiers, and
 * authorization codes across IDK.
 *
 * Two API layers:
 *
 * 1. **Command-oriented** (primary): [generateToken] and [nextBytes] each take typed
 *    args and return [IdkResult]. These go through the [Command][com.sphereon.core.api.session.Command]
 *    infrastructure so any interceptors (policy, audit, telemetry) configured for this
 *    service apply uniformly. The same commands are exposed via [commands] for callers
 *    that want to route or schedule them directly.
 *
 * 2. **Convenience** ([newToken], [randomBytes]): thin wrappers that delegate to the
 *    commands above and unwrap the happy path. Intended for call sites where CSPRNG
 *    failure on valid input would be a programming bug rather than a recoverable
 *    error — they throw [IllegalStateException] if the underlying command returns Err.
 *
 * Implementations MUST draw from a platform CSPRNG (e.g.
 * `dev.whyoleg.cryptography.random.CryptographyRandom.Default`) — never
 * `kotlin.random.Random.Default` — since output is exposed in user-facing token formats
 * (authorization codes, access tokens, PAR request URIs, nonces).
 */
interface SecureRandom : ServiceFacade {
    override val serviceId: String get() = SERVICE_ID

    /**
     * Generate a random token via [Commands.generateToken].
     *
     * @param args token length and encoding — defaults to 32 bytes / base64url
     */
    suspend fun generateToken(args: GenerateTokenArgs = GenerateTokenArgs()): IdkResult<StringResult, IdkError>

    /** Generate random bytes via [Commands.nextBytes]. */
    suspend fun nextBytes(args: NextBytesArgs): IdkResult<ByteArrayResult, IdkError>

    /** Underlying commands for advanced usage (routing, direct execution, registry lookup). */
    val commands: Commands

    /**
     * Convenience: generate an encoded random token. Delegates to [generateToken] and
     * throws on unexpected failure (CSPRNG never fails for valid input).
     */
    suspend fun newToken(
        lengthBytes: Int = DEFAULT_TOKEN_BYTES,
        encoding: Encoding = Encoding.BASE64URL,
    ): String {
        val result = generateToken(GenerateTokenArgs(lengthBytes = lengthBytes, encoding = encoding))
        return if (result.isOk) {
            result.value.value
        } else {
            error("$SERVICE_ID.${GenerateTokenCommand.COMMAND_ID} failed unexpectedly for valid input: ${result.error.message}")
        }
    }

    /**
     * Convenience: produce raw random bytes. Delegates to [nextBytes] and throws on
     * unexpected failure (CSPRNG never fails for valid input).
     */
    suspend fun randomBytes(length: Int): ByteArray {
        val result = nextBytes(NextBytesArgs(length = length))
        return if (result.isOk) {
            result.value.bytes
        } else {
            error("$SERVICE_ID.${NextBytesCommand.COMMAND_ID} failed unexpectedly for valid input: ${result.error.message}")
        }
    }

    /** Container exposing the commands this service delegates to. */
    interface Commands {
        val generateToken: GenerateTokenCommand
        val nextBytes: NextBytesCommand
    }

    public companion object {
        public const val SERVICE_ID: String = "core.random"
    }
}
