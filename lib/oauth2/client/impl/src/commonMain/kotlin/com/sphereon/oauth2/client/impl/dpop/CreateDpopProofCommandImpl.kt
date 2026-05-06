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
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.command.CreateDpopProofArgs
import com.sphereon.oauth2.common.command.CreateDpopProofCommand
import com.sphereon.oauth2.common.error.DpopError
import com.sphereon.oauth2.common.model.CreateDpopProofOptions
import com.sphereon.oauth2.common.model.DpopJwtHeader
import com.sphereon.oauth2.common.model.DpopJwtPayload
import com.sphereon.oauth2.common.model.DpopProofResult
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock

/**
 * Implementation of CreateDpopProofCommand
 *
 * Creates DPoP proof JWTs as defined in RFC 9449.
 */
@Inject
@SingleIn(SessionScope::class)
class CreateDpopProofCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService,
    private val secureRandom: SecureRandom,
) : TypedServiceCommandAdapter<CreateDpopProofArgs, DpopProofResult, IdkError>(
        commandId = CreateDpopProofCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateDpopProofArgs>(),
        outputTypeToken = typeToken<DpopProofResult>(),
    ),
    CreateDpopProofCommand {
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
            // Generate unique JTI
            val jti = generateJti()

            // Calculate access token hash if needed
            val ath = options.accessToken?.let { calculateAccessTokenHash(it) }

            // Normalize URL (remove query and fragment)
            val normalizedHtu = normalizeUrl(options.httpUrl)

            // Get timestamp
            val iat = options.issuedAt ?: Clock.System.now().epochSeconds

            // Build DPoP header
            val algorithm = determineAlgorithm(publicJwk)
            val header =
                DpopJwtHeader(
                    typ = "dpop+jwt",
                    alg = algorithm,
                    jwk = publicJwk,
                )

            // Build DPoP payload
            val payload =
                DpopJwtPayload(
                    jti = jti,
                    htm = options.httpMethod.uppercase(),
                    htu = normalizedHtu,
                    iat = iat,
                    ath = ath,
                    nonce = options.nonce,
                )

            // Serialize header and payload to JSON
            val headerJson = Json.encodeToJsonElement(header).jsonObject
            val payloadString = Json.encodeToString(payload)

            // Create JWT using JwtService with the issuer from options
            val jwsArgs =
                CreateJwsArgs(
                    issuer = options.issuer,
                    payload = payloadString,
                    opts =
                        CreateJwsOpts(
                            protectedHeader = headerJson,
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

            val dpopProof = jwtResult.jwt // JwtCompactResult.jwt is a String
            val jwkThumbprint = generateJwkThumbprint(publicJwk)

            return Ok(
                DpopProofResult(
                    dpopProof = dpopProof,
                    jwkThumbprint = jwkThumbprint,
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

    /**
     * Generates a unique JWT ID using a random UUID-like string
     */
    private suspend fun generateJti(): String =
        // 16 random bytes (128 bits) encoded as base64url
        secureRandom.newToken(lengthBytes = JTI_RANDOM_BYTES)

    /**
     * Calculates the SHA-256 hash of an access token (RFC 9449 Section 4.2)
     * The hash is base64url-encoded
     */
    private fun calculateAccessTokenHash(accessToken: String): String {
        val tokenBytes = accessToken.encodeToByteArray()
        val hashBytes = hash(tokenBytes, DigestAlg.SHA256)
        return hashBytes.encodeToBase64Url()
    }

    /**
     * Normalizes a URL by removing query parameters and fragment
     * RFC 9449 requires htu to contain only scheme, host, port, and path
     */
    private fun normalizeUrl(url: String): String {
        // Find the position of '?' or '#'
        val queryStart = url.indexOf('?')
        val fragmentStart = url.indexOf('#')

        // Determine where to cut the URL
        val cutPosition =
            when {
                queryStart != -1 && fragmentStart != -1 -> minOf(queryStart, fragmentStart)
                queryStart != -1 -> queryStart
                fragmentStart != -1 -> fragmentStart
                else -> url.length
            }

        return url.substring(0, cutPosition)
    }

    /**
     * Determines the signing algorithm from the JWK
     * Maps JWK key types and curves to appropriate signing algorithms
     */
    private fun determineAlgorithm(jwk: Jwk): String {
        // If alg is explicitly set in JWK, use it
        jwk.alg?.let { return it.value }

        // Otherwise, infer from key type and curve
        return when (jwk.kty.value) {
            "RSA" -> {
                "RS256"
            }

            // Default RSA algorithm
            "EC" -> {
                when (jwk.crv?.value) {
                    "P-256" -> "ES256"
                    "P-384" -> "ES384"
                    "P-521" -> "ES512"
                    "secp256k1" -> "ES256K"
                    else -> throw IllegalArgumentException("Unsupported EC curve: ${jwk.crv?.value}")
                }
            }

            "OKP" -> {
                when (jwk.crv?.value) {
                    "Ed25519" -> "EdDSA"
                    else -> throw IllegalArgumentException("Unsupported OKP curve: ${jwk.crv?.value}")
                }
            }

            else -> {
                throw IllegalArgumentException("Unsupported key type for DPoP: ${jwk.kty.value}")
            }
        }
    }

    companion object {
        private const val JTI_RANDOM_BYTES = 16
    }
}
