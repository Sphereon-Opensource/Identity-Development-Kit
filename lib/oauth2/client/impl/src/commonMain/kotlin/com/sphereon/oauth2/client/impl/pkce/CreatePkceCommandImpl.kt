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

package com.sphereon.oauth2.client.impl.pkce

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.api.validation.toIdkResult
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.client.validation.validatePkceData
import com.sphereon.oauth2.common.error.PkceError
import com.sphereon.oauth2.common.model.PkceMethod
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of CreatePkceCommand for generating PKCE challenge/verifier pairs
 */
@Inject
@SingleIn(SessionScope::class)
class CreatePkceCommandImpl(
    execution: SessionExecution,
    private val secureRandom: SecureRandom,
) : TypedServiceCommandAdapter<CreatePkceArgs, PkceData, IdkError>(
        commandId = CreatePkceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreatePkceArgs>(),
        outputTypeToken = typeToken<PkceData>(),
    ),
    CreatePkceCommand {
    override val commandId: String get() = CreatePkceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreatePkceArgs

    override suspend fun doExecute(
        args: CreatePkceArgs,
        applyDuring: (CreatePkceArgs) -> CreatePkceArgs,
    ): IdkResult<PkceData, IdkError> {
        val applied = applyDuring(args)
        return createPkceInternal(applied.codeVerifier, applied.allowedMethods).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun createPkceInternal(
        codeVerifier: String?,
        allowedMethods: List<PkceMethod>,
    ): IdkResult<PkceData, PkceError> {
        if (allowedMethods.isEmpty()) {
            return Err(PkceError.NoMethodsAllowed).asResult()
        }

        return try {
            val verifier = codeVerifier ?: generateCodeVerifier()

            // Prefer S256 over PLAIN for security
            val method =
                allowedMethods.firstOrNull { it == PkceMethod.S256 }
                    ?: allowedMethods.first()

            val challenge = calculateCodeChallenge(verifier, method)

            val pkceData =
                PkceData(
                    codeVerifier = verifier,
                    codeChallenge = challenge,
                    codeChallengeMethod = method,
                )

            // Validate using Konform
            validatePkceData(pkceData).toIdkResult { errors ->
                PkceError.GenerationFailed(
                    reason = "PKCE validation failed: ${errors.joinToString("; ") { it.message }}",
                )
            }
        } catch (expected: Exception) {
            Err(
                PkceError.GenerationFailed(
                    reason = expected.message ?: "Unknown error during PKCE generation",
                ),
            ).asResult()
        }
    }

    /**
     * Generates a cryptographically secure random code verifier.
     *
     * RFC 7636 requires:
     * - Length: 43-128 characters
     * - Character set: `[A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~"` (unreserved)
     *
     * 64 random bytes (512 bits) encoded as unpadded base64url → 86 characters.
     */
    private suspend fun generateCodeVerifier(): String = secureRandom.newToken(lengthBytes = CODE_VERIFIER_RANDOM_BYTES)

    /**
     * Calculates the code challenge from the code verifier
     *
     * @param verifier The code verifier
     * @param method The code challenge method (plain or S256)
     * @return The code challenge
     */
    private fun calculateCodeChallenge(
        verifier: String,
        method: PkceMethod,
    ): String =
        when (method) {
            PkceMethod.PLAIN -> {
                verifier
            }

            PkceMethod.S256 -> {
                // Use IDK's crypto infrastructure for SHA256
                val hashBytes = hash(verifier.encodeToByteArray(), DigestAlg.SHA256)
                hashBytes.encodeToBase64Url()
            }
        }

    companion object {
        private const val CODE_VERIFIER_RANDOM_BYTES = 64
    }
}
