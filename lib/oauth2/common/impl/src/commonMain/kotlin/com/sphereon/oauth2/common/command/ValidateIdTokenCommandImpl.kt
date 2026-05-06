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

package com.sphereon.oauth2.common.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.api.validation.toIdkResult
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.crypto.core.json.cryptoJsonSerializer
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.IdTokenPayload
import com.sphereon.oauth2.common.model.IdTokenValidationOptions
import com.sphereon.oauth2.common.model.ValidatedIdToken
import com.sphereon.oauth2.common.validation.buildOidcAudienceAzpValidation
import com.sphereon.oauth2.common.validation.isAuthenticationFresh
import com.sphereon.oauth2.common.validation.isIdTokenExpired
import com.sphereon.oauth2.common.validation.isIdTokenIssuedAtValid
import com.sphereon.oauth2.common.validation.jwsAlgToDigest
import com.sphereon.oauth2.common.validation.validateIdTokenPayload
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Implementation of ValidateIdTokenCommand
 *
 * This command performs complete OpenID Connect ID Token validation including:
 * 1. JWT signature verification (delegates to JwtService)
 * 2. Issuer validation
 * 3. Audience validation
 * 4. Expiration validation
 * 5. Issued-at validation
 * 6. Nonce validation
 * 7. at_hash validation (if access token provided)
 * 8. c_hash validation (if authorization code provided)
 * 9. Authentication time validation (if max_age specified)
 * 10. ACR validation (if expected ACR specified)
 */
@Inject
@SingleIn(SessionScope::class)
class ValidateIdTokenCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService,
) : TypedServiceCommandAdapter<ValidateIdTokenArgs, ValidatedIdToken, IdkError>(
        commandId = ValidateIdTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ValidateIdTokenArgs>(),
        outputTypeToken = typeToken<ValidatedIdToken>(),
    ),
    ValidateIdTokenCommand {
    override val commandId: String get() = ValidateIdTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ValidateIdTokenArgs

    override suspend fun doExecute(
        args: ValidateIdTokenArgs,
        applyDuring: (ValidateIdTokenArgs) -> ValidateIdTokenArgs,
    ): IdkResult<ValidatedIdToken, IdkError> {
        val applied = applyDuring(args)
        return validateIdTokenInternal(applied.idToken, applied.options).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun validateIdTokenInternal(
        idToken: String,
        options: IdTokenValidationOptions,
    ): IdkResult<ValidatedIdToken, Oauth2Error> {
        // Pre-validate header against OIDF constraints before touching crypto — alg allow-list,
        // reject embedded keys, enforce JWKS-bound `kid` when a trusted key set was provided.
        val header =
            parseJwsHeader(idToken)
                ?: return Oauth2Error
                    .InvalidIdToken(reason = "ID Token JWS header could not be parsed")
                    .asErrorResult()

        val alg = header["alg"]?.jsonPrimitive?.content
        if (alg.isNullOrBlank()) {
            return Oauth2Error.InvalidIdToken(reason = "ID Token JWS header is missing 'alg'").asErrorResult()
        }
        if (alg !in options.allowedAlgorithms) {
            return Oauth2Error
                .InvalidIdToken(
                    reason = "ID Token alg '$alg' is not in the allow-list ${options.allowedAlgorithms}",
                ).asErrorResult()
        }

        if (!options.allowEmbeddedKeyInHeader) {
            if (header.containsKey("jwk")) {
                return Oauth2Error
                    .InvalidIdToken(reason = "ID Token JWS header must not contain embedded 'jwk'")
                    .asErrorResult()
            }
            if (header.containsKey("x5c")) {
                return Oauth2Error
                    .InvalidIdToken(reason = "ID Token JWS header must not contain embedded 'x5c' chain")
                    .asErrorResult()
            }
        }

        val trustedJwks = options.trustedJwks
        // kid resolution and signature verification both happen inside the JWS verifier when
        // trustedJwks is supplied. The verifier refuses embedded jwk/x5c headers in that mode and
        // only accepts a key drawn from the trusted set, so no standalone precheck is needed here.

        // Step 1: Verify JWT signature using JwtService
        val verificationResult =
            jwtService.verifyJws(
                VerifyJwsArgs(
                    jws = JwsCompact(idToken),
                    trustedJwks = trustedJwks?.asJsonObject(),
                ),
            )

        if (verificationResult.isErr) {
            return Oauth2Error
                .InvalidIdToken(
                    reason = "ID Token signature verification failed: ${verificationResult.error.message}",
                ).asErrorResult()
        }

        val jwsResult = verificationResult.value
        if (!jwsResult.isValid) {
            return Oauth2Error
                .InvalidIdToken(
                    reason = "ID Token signature is invalid: ${jwsResult.errorMessages.joinToString(", ")}",
                ).asErrorResult()
        }

        // Step 2: Parse ID Token payload (already decoded during verification)
        val payload =
            try {
                Json.decodeFromJsonElement(IdTokenPayload.serializer(), jwsResult.parsedPayload)
            } catch (expected: Exception) {
                return Oauth2Error
                    .InvalidIdToken(
                        reason = "Failed to parse ID Token payload: ${expected.message}",
                        exception = expected,
                    ).asErrorResult()
            }

        // Step 3: Validate payload structure
        val structureValidation = validateIdTokenPayload(payload)
        if (!structureValidation.isValid) {
            return Oauth2Error
                .InvalidIdToken(
                    reason = "ID Token structure invalid: ${structureValidation.errors.joinToString(", ")}",
                ).asErrorResult()
        }

        // Step 4: Validate issuer
        if (payload.iss != options.expectedIssuer) {
            return Oauth2Error
                .InvalidIdToken(
                    reason = "Issuer mismatch: expected '${options.expectedIssuer}', got '${payload.iss}'",
                ).asErrorResult()
        }

        // Step 5: Validate audience + azp (OIDC Core §3.1.3.7 steps 3-6) via Konform.
        val audAzpValidation =
            buildOidcAudienceAzpValidation(options.expectedAudience)(payload)
                .toIdkResult { errors ->
                    Oauth2Error.InvalidIdToken(
                        reason = errors.joinToString("; ") { it.message },
                    )
                }
        if (audAzpValidation.isErr) return audAzpValidation.error.asErrorResult()

        // Step 6: Validate expiration
        if (isIdTokenExpired(payload.exp, options.clockSkewSeconds)) {
            return Oauth2Error
                .InvalidIdToken(
                    reason = "ID Token has expired (exp: ${payload.exp})",
                ).asErrorResult()
        }

        // Step 7: Validate issued-at
        if (!isIdTokenIssuedAtValid(payload.iat, options.clockSkewSeconds)) {
            return Oauth2Error
                .InvalidIdToken(
                    reason = "ID Token issued-at time is in the future (iat: ${payload.iat})",
                ).asErrorResult()
        }

        // Step 8: Validate nonce (if expected)
        val nonceMatched =
            if (options.expectedNonce != null) {
                if (payload.nonce == null) {
                    return Oauth2Error
                        .InvalidIdToken(
                            reason = "Nonce is required but missing in ID Token",
                        ).asErrorResult()
                }
                if (payload.nonce != options.expectedNonce) {
                    return Oauth2Error
                        .InvalidIdToken(
                            reason = "Nonce mismatch: expected '${options.expectedNonce}', got '${payload.nonce}'",
                        ).asErrorResult()
                }
                true
            } else {
                null
            }

        // Step 9: Validate at_hash (if access token provided)
        val accessToken = options.accessToken
        val atHash = payload.atHash
        val atHashMatched =
            if (accessToken != null && atHash != null) {
                val algorithm = extractAlgorithmFromJws(jwsResult.jws)
                val isValid = validateTokenHash(accessToken, atHash, algorithm)
                if (!isValid) {
                    return Oauth2Error
                        .InvalidIdToken(
                            reason = "at_hash validation failed",
                        ).asErrorResult()
                }
                true
            } else {
                null
            }

        // Step 10: Validate c_hash (if authorization code provided)
        val authorizationCode = options.authorizationCode
        val cHash = payload.cHash
        val cHashMatched =
            if (authorizationCode != null && cHash != null) {
                val algorithm = extractAlgorithmFromJws(jwsResult.jws)
                val isValid = validateTokenHash(authorizationCode, cHash, algorithm)
                if (!isValid) {
                    return Oauth2Error
                        .InvalidIdToken(
                            reason = "c_hash validation failed",
                        ).asErrorResult()
                }
                true
            } else {
                null
            }

        // Step 11: Validate authentication time (if max_age specified)
        val maxAge = options.maxAge
        if (maxAge != null) {
            val authTime =
                payload.authTime
                    ?: if (options.requireAuthTime) {
                        return Oauth2Error
                            .InvalidIdToken(
                                reason = "auth_time is required but missing",
                            ).asErrorResult()
                    } else {
                        // If auth_time not present and not required, skip this check
                        null
                    }

            if (authTime != null && !isAuthenticationFresh(authTime, maxAge, options.clockSkewSeconds)) {
                return Oauth2Error
                    .InvalidIdToken(
                        reason = "Authentication is too old (auth_time: $authTime, max_age: $maxAge)",
                    ).asErrorResult()
            }
        } else if (options.requireAuthTime && payload.authTime == null) {
            return Oauth2Error
                .InvalidIdToken(
                    reason = "auth_time is required but missing",
                ).asErrorResult()
        }

        // Step 12: Validate ACR (if expected)
        if (options.expectedAcr != null) {
            if (payload.acr != options.expectedAcr) {
                return Oauth2Error
                    .InvalidIdToken(
                        reason = "ACR mismatch: expected '${options.expectedAcr}', got '${payload.acr}'",
                    ).asErrorResult()
            }
        }

        // All validations passed
        return ValidatedIdToken(
            payload = payload,
            rawIdToken = idToken,
            nonceMatched = nonceMatched,
            atHashMatched = atHashMatched,
            cHashMatched = cHashMatched,
        ).asOkResult()
    }

    /**
     * Parse the JWS protected header from the compact serialisation without touching the
     * signature verifier — used for OIDF-level pre-validation (alg allow-list, embedded key
     * rejection, `kid`/JWKS binding). Returns `null` on malformed input; callers surface the
     * validation failure themselves rather than throwing.
     */
    private fun parseJwsHeader(idToken: String): JsonObject? {
        val firstDot = idToken.indexOf('.')
        if (firstDot <= 0) return null
        val headerSegment = idToken.substring(0, firstDot)
        return try {
            val decoded = headerSegment.decodeFromBase64Url().decodeToString()
            Json.parseToJsonElement(decoded).jsonObject
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Render a [JwkSet] as a `{"keys": [...]}` JSON object so the verifier can apply RFC 7517
     * key selection without a typed dependency on the JWKS data class.
     */
    private fun JwkSet.asJsonObject(): JsonObject = cryptoJsonSerializer.encodeToJsonElement(JwkSet.serializer(), this).jsonObject

    /**
     * Extracts the signing algorithm from the JWS header
     */
    private fun extractAlgorithmFromJws(jws: com.sphereon.crypto.jose.jws.JwsJsonGeneralWithIdentifiers): String {
        // Get the first signature's pre-parsed protected header
        val firstSignature =
            jws.signatures.firstOrNull()
                ?: throw IllegalArgumentException("JWS has no signatures")

        val protectedHeader = firstSignature.parsedProtectedHeader

        val alg =
            protectedHeader["alg"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("JWS protected header missing 'alg' field")

        return alg
    }

    /**
     * Validates token hash (at_hash or c_hash)
     *
     * OpenID Connect Core Section 3.1.3.3:
     * 1. Hash the token using the hash algorithm for the alg
     * 2. Take the left-most half of the hash
     * 3. Base64url encode it
     * 4. Compare with expected hash
     */
    private fun validateTokenHash(
        token: String,
        expectedHash: String,
        algorithm: String,
    ): Boolean =
        try {
            val digestAlg =
                jwsAlgToDigest(algorithm)
                    ?: throw IllegalArgumentException("Unsupported algorithm: $algorithm")
            val hashBytes = hash(token.encodeToByteArray(), digestAlg)
            val leftHalf = hashBytes.copyOfRange(0, hashBytes.size / 2)
            leftHalf.encodeToBase64Url() == expectedHash
        } catch (_: Exception) {
            false
        }
}
