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

package com.sphereon.oauth2.client.impl.dpop

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.command.CreateDpopProofArgs
import com.sphereon.oauth2.common.command.CreateDpopProofCommand
import com.sphereon.oauth2.common.command.DpopProofAssembly
import com.sphereon.oauth2.common.command.DpopProofAssemblyRequest
import com.sphereon.oauth2.common.error.DpopError
import com.sphereon.oauth2.common.model.CreateDpopProofOptions
import com.sphereon.oauth2.common.model.DpopProofResult
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of CreateDpopProofCommand
 *
 * Creates DPoP proof JWTs as defined in RFC 9449.
 *
 * Header/payload assembly (jti generation, access-token-hash computation, URL normalization,
 * algorithm inference) is delegated to [DpopProofAssembly]: the SAME seam the wallet WSCA
 * local-signing path (`LocalWsca`) uses, so both signers build byte-for-byte
 * structurally identical DPoP proofs from one implementation. Only the actual signing differs -
 * this command signs via the KMS-backed [JwtService]; `LocalWsca` signs via a `Wscd`.
 */
@Inject
@SingleIn(SessionScope::class)
class CreateDpopProofCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService,
    secureRandom: SecureRandom,
) : TypedServiceCommandAdapter<CreateDpopProofArgs, DpopProofResult, IdkError>(
        commandId = CreateDpopProofCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateDpopProofArgs>(),
        outputTypeToken = typeToken<DpopProofResult>(),
    ),
    CreateDpopProofCommand {
    /**
     * Constructed directly rather than injected, so this command's public constructor - and its
     * existing direct-construction call sites in tests - stay unchanged. [DpopProofAssembly] is a
     * stateless, config-free helper (its only dependency is [secureRandom], which this command
     * already receives), so a private instance here behaves identically to the DI-managed
     * singleton `LocalWsca` receives.
     */
    private val dpopProofAssembly = DpopProofAssembly(secureRandom)

    override val commandId: String get() = CreateDpopProofCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateDpopProofArgs

    override suspend fun doExecute(
        args: CreateDpopProofArgs,
        applyDuring: (CreateDpopProofArgs) -> CreateDpopProofArgs,
    ): IdkResult<DpopProofResult, IdkError> {
        val applied = applyDuring(args)
        return createDpopProofInternal(applied.options, applied.publicJwk).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun createDpopProofInternal(
        options: CreateDpopProofOptions,
        publicJwk: Jwk,
    ): IdkResult<DpopProofResult, DpopError> {
        try {
            val assembled =
                dpopProofAssembly.assemble(
                    DpopProofAssemblyRequest(
                        httpMethod = options.httpMethod,
                        httpUrl = options.httpUrl,
                        nonce = options.nonce,
                        accessToken = options.accessToken,
                        issuedAt = options.issuedAt,
                    ),
                    publicJwk,
                )

            // Create JWT using JwtService with the issuer from options
            val jwsArgs =
                CreateJwsArgs(
                    issuer = options.issuer,
                    payload = assembled.payloadJsonString,
                    opts =
                        CreateJwsOpts(
                            protectedHeader = assembled.headerJson,
                            noIssPayloadUpdate = true, // Don't add iss to payload
                            noIdentifierInHeader = true, // Don't add kid to header (we use jwk)
                        ),
                )

            // Sign the JWT
            val jwtResult =
                jwtService.createJwsCompact(jwsArgs).getOrElse { error ->
                    return Err(
                        DpopError.GenerationFailed(
                            reason = "Failed to sign DPoP JWT: ${error.message.defaultMessage}",
                            exception = error.exception,
                        ),
                    )
                }

            return Ok(
                DpopProofResult(
                    dpopProof = jwtResult.jwt, // JwtCompactResult.jwt is a String
                    jwkThumbprint = assembled.jwkThumbprint,
                ),
            )
        } catch (expected: Exception) {
            return Err(
                DpopError.GenerationFailed(
                    reason = "DPoP proof generation failed: ${expected.message}",
                    exception = expected,
                ),
            )
        }
    }
}
