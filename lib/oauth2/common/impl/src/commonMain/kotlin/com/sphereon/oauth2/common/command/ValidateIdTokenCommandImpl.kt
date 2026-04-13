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
 */

package com.sphereon.oauth2.common.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.VerifyJwsArgs
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.IdTokenPayload
import com.sphereon.oauth2.common.model.IdTokenValidationOptions
import com.sphereon.oauth2.common.model.ValidatedIdToken
import com.sphereon.oauth2.common.validation.isAuthenticationFresh
import com.sphereon.oauth2.common.validation.isIdTokenExpired
import com.sphereon.oauth2.common.validation.isIdTokenIssuedAtValid
import com.sphereon.oauth2.common.validation.validateAudience
import com.sphereon.oauth2.common.validation.validateIdTokenPayload
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

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
    private val jwtService: JwtService
) : TypedServiceCommandAdapter<ValidateIdTokenArgs, ValidatedIdToken>(
    commandId = ValidateIdTokenCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ValidateIdTokenArgs>(),
    outputTypeToken = typeToken<ValidatedIdToken>(),
), ValidateIdTokenCommand {

    override val commandId: String get() = ValidateIdTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ValidateIdTokenArgs

    override suspend fun doExecute(
        args: ValidateIdTokenArgs,
        applyDuring: (ValidateIdTokenArgs) -> ValidateIdTokenArgs
    ): IdkResult<ValidatedIdToken, IdkError> {
        val applied = applyDuring(args)
        return validateIdTokenInternal(applied.idToken, applied.options).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun validateIdTokenInternal(
        idToken: String,
        options: IdTokenValidationOptions
    ): IdkResult<ValidatedIdToken, Oauth2Error> {
        // Step 1: Verify JWT signature using JwtService
        val verificationResult = jwtService.verifyJws(
            VerifyJwsArgs(
                jws = JwsCompact(idToken)
            )
        )

        if (verificationResult.isErr) {
            return Oauth2Error.InvalidIdToken(
                reason = "ID Token signature verification failed: ${verificationResult.error.message}"
            ).asErrorResult()
        }

        val jwsResult = verificationResult.value
        if (!jwsResult.isValid) {
            return Oauth2Error.InvalidIdToken(
                reason = "ID Token signature is invalid: ${jwsResult.errorMessages.joinToString(", ")}"
            ).asErrorResult()
        }

        // Step 2: Parse ID Token payload
        val payload = try {
            // The payload from JWS is base64url encoded, decode it first
            val payloadBase64 = jwsResult.jws.payload
            val payloadBytes = payloadBase64.decodeFromBase64Url()
            val payloadJson = payloadBytes.decodeToString()
            Json.decodeFromString(IdTokenPayload.serializer(), payloadJson)
        } catch (e: Exception) {
            return Oauth2Error.InvalidIdToken(
                reason = "Failed to parse ID Token payload: ${e.message}",
                exception = e
            ).asErrorResult()
        }

        // Step 3: Validate payload structure
        val structureValidation = validateIdTokenPayload(payload)
        if (!structureValidation.isValid) {
            return Oauth2Error.InvalidIdToken(
                reason = "ID Token structure invalid: ${structureValidation.errors.joinToString(", ")}"
            ).asErrorResult()
        }

        // Step 4: Validate issuer
        if (payload.iss != options.expectedIssuer) {
            return Oauth2Error.InvalidIdToken(
                reason = "Issuer mismatch: expected '${options.expectedIssuer}', got '${payload.iss}'"
            ).asErrorResult()
        }

        // Step 5: Validate audience
        if (!validateAudience(payload.aud, options.expectedAudience)) {
            return Oauth2Error.InvalidIdToken(
                reason = "Audience validation failed: expected '${options.expectedAudience}' in ${payload.aud}"
            ).asErrorResult()
        }

        // Step 6: Validate expiration
        if (isIdTokenExpired(payload.exp, options.clockSkewSeconds)) {
            return Oauth2Error.InvalidIdToken(
                reason = "ID Token has expired (exp: ${payload.exp})"
            ).asErrorResult()
        }

        // Step 7: Validate issued-at
        if (!isIdTokenIssuedAtValid(payload.iat, options.clockSkewSeconds)) {
            return Oauth2Error.InvalidIdToken(
                reason = "ID Token issued-at time is in the future (iat: ${payload.iat})"
            ).asErrorResult()
        }

        // Step 8: Validate nonce (if expected)
        val nonceMatched = if (options.expectedNonce != null) {
            if (payload.nonce == null) {
                return Oauth2Error.InvalidIdToken(
                    reason = "Nonce is required but missing in ID Token"
                ).asErrorResult()
            }
            if (payload.nonce != options.expectedNonce) {
                return Oauth2Error.InvalidIdToken(
                    reason = "Nonce mismatch: expected '${options.expectedNonce}', got '${payload.nonce}'"
                ).asErrorResult()
            }
            true
        } else {
            null
        }

        // Step 9: Validate at_hash (if access token provided)
        val accessToken = options.accessToken
        val atHash = payload.atHash
        val atHashMatched = if (accessToken != null && atHash != null) {
            val algorithm = extractAlgorithmFromJws(jwsResult.jws)
            val isValid = validateTokenHash(accessToken, atHash, algorithm)
            if (!isValid) {
                return Oauth2Error.InvalidIdToken(
                    reason = "at_hash validation failed"
                ).asErrorResult()
            }
            true
        } else {
            null
        }

        // Step 10: Validate c_hash (if authorization code provided)
        val authorizationCode = options.authorizationCode
        val cHash = payload.cHash
        val cHashMatched = if (authorizationCode != null && cHash != null) {
            val algorithm = extractAlgorithmFromJws(jwsResult.jws)
            val isValid = validateTokenHash(authorizationCode, cHash, algorithm)
            if (!isValid) {
                return Oauth2Error.InvalidIdToken(
                    reason = "c_hash validation failed"
                ).asErrorResult()
            }
            true
        } else {
            null
        }

        // Step 11: Validate authentication time (if max_age specified)
        val maxAge = options.maxAge
        if (maxAge != null) {
            val authTime = payload.authTime
                ?: if (options.requireAuthTime) {
                    return Oauth2Error.InvalidIdToken(
                        reason = "auth_time is required but missing"
                    ).asErrorResult()
                } else {
                    // If auth_time not present and not required, skip this check
                    null
                }

            if (authTime != null && !isAuthenticationFresh(authTime, maxAge, options.clockSkewSeconds)) {
                return Oauth2Error.InvalidIdToken(
                    reason = "Authentication is too old (auth_time: $authTime, max_age: $maxAge)"
                ).asErrorResult()
            }
        } else if (options.requireAuthTime && payload.authTime == null) {
            return Oauth2Error.InvalidIdToken(
                reason = "auth_time is required but missing"
            ).asErrorResult()
        }

        // Step 12: Validate ACR (if expected)
        if (options.expectedAcr != null) {
            if (payload.acr != options.expectedAcr) {
                return Oauth2Error.InvalidIdToken(
                    reason = "ACR mismatch: expected '${options.expectedAcr}', got '${payload.acr}'"
                ).asErrorResult()
            }
        }

        // All validations passed
        return ValidatedIdToken(
            payload = payload,
            rawIdToken = idToken,
            nonceMatched = nonceMatched,
            atHashMatched = atHashMatched,
            cHashMatched = cHashMatched
        ).asOkResult()
    }

    /**
     * Extracts the signing algorithm from the JWS header
     */
    private fun extractAlgorithmFromJws(jws: com.sphereon.crypto.jose.jws.JwsJsonGeneralWithIdentifiers): String {
        // Get the first signature's protected header
        val firstSignature = jws.signatures.firstOrNull()
            ?: throw IllegalArgumentException("JWS has no signatures")

        // Decode the protected header
        val protectedHeaderJson = firstSignature.protected.decodeFromBase64Url().decodeToString()
        val protectedHeader = Json.parseToJsonElement(protectedHeaderJson).jsonObject
        val alg = protectedHeader["alg"]?.jsonPrimitive?.content
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
    private fun validateTokenHash(token: String, expectedHash: String, algorithm: String): Boolean {
        try {
            // Map JWT algorithm to digest algorithm
            val digestAlg = when (algorithm) {
                "RS256", "ES256", "PS256", "HS256" -> DigestAlg.SHA256
                "RS384", "ES384", "PS384", "HS384" -> DigestAlg.SHA384
                "RS512", "ES512", "PS512", "HS512" -> DigestAlg.SHA512
                else -> throw IllegalArgumentException("Unsupported algorithm: $algorithm")
            }

            // Hash the token
            val tokenBytes = token.encodeToByteArray()
            val hashBytes = hash(tokenBytes, digestAlg)

            // Take left-most half
            val halfLength = hashBytes.size / 2
            val leftHalf = hashBytes.copyOfRange(0, halfLength)

            // Base64url encode
            val actualHash = leftHalf.encodeToBase64Url()

            return actualHash == expectedHash
        } catch (e: Exception) {
            // Hash validation failed
            return false
        }
    }
}
