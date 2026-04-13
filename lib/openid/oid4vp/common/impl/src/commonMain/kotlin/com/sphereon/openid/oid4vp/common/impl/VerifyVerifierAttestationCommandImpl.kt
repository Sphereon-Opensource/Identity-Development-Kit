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

package com.sphereon.openid.oid4vp.common.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.Encoding
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jws.Jws
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.VerifyJwsCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.ClientIdValidationErrorType
import com.sphereon.openid.oid4vp.common.VerifierAttestationJwtConstants
import com.sphereon.openid.oid4vp.common.VerifierAttestationValidationError
import com.sphereon.openid.oid4vp.common.VerifyVerifierAttestationArgs
import com.sphereon.openid.oid4vp.common.VerifyVerifierAttestationCommand
import com.sphereon.openid.oid4vp.common.VerifyVerifierAttestationCommandService
import com.sphereon.openid.oid4vp.common.VerifyVerifierAttestationResult
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of VerifyVerifierAttestationCommand.
 *
 * Verifies a verifier attestation JWT per OpenID4VP 1.0 Final Section 12.
 *
 * Verification Steps:
 * 1. Parse JWT and validate structure
 * 2. Validate typ header is "verifier-attestation+jwt"
 * 3. Verify JWT signature (using identifier from header: kid, jwk, x5c)
 * 4. Validate issuer (iss) is in trusted issuers list
 * 5. Validate subject (sub) matches expected client_id
 * 6. Validate expiration (exp) and not-before (nbf)
 * 7. Validate required claims present (iss, sub, exp, cnf, cnf.jwk)
 * 8. Validate cnf.jwk matches JAR signer key
 */
@Inject
@SingleIn(SessionScope::class)
class VerifyVerifierAttestationCommandImpl(
    execution: SessionExecution,
    private val verifyJwsCommand: VerifyJwsCommand,
) : TypedServiceCommandAdapter<VerifyVerifierAttestationArgs, VerifyVerifierAttestationResult>(
    commandId = VerifyVerifierAttestationCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<VerifyVerifierAttestationArgs>(),
    outputTypeToken = typeToken<VerifyVerifierAttestationResult>(),
), VerifyVerifierAttestationCommand, VerifyVerifierAttestationCommandService {

    override val commandId: String get() = VerifyVerifierAttestationCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyVerifierAttestationArgs

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun verifyVerifierAttestation(
        args: VerifyVerifierAttestationArgs
    ): IdkResult<VerifyVerifierAttestationResult, IdkError> {
        return execute(args)
    }

    override suspend fun doExecute(
        args: VerifyVerifierAttestationArgs,
        applyDuring: (VerifyVerifierAttestationArgs) -> VerifyVerifierAttestationArgs
    ): IdkResult<VerifyVerifierAttestationResult, IdkError> {
        val processedArgs = applyDuring(args)
        val errors = mutableListOf<VerifierAttestationValidationError>()

        // 1. Parse JWT structure
        val jwtParts = processedArgs.attestationJwt.split(".")
        if (jwtParts.size != 3) {
            return Ok(VerifyVerifierAttestationResult(
                valid = false,
                errors = listOf(VerifierAttestationValidationError(
                    type = ClientIdValidationErrorType.VALIDATION_ERROR,
                    message = "Invalid JWT structure",
                    details = "Expected 3 parts separated by '.', got ${jwtParts.size}"
                ))
            ))
        }

        // 2. Decode and parse header
        val header = try {
            val headerJson = jwtParts[0].decodeFrom(Encoding.BASE64URL).decodeToString()
            json.parseToJsonElement(headerJson).jsonObject
        } catch (e: Exception) {
            return Ok(VerifyVerifierAttestationResult(
                valid = false,
                errors = listOf(VerifierAttestationValidationError(
                    type = ClientIdValidationErrorType.VALIDATION_ERROR,
                    message = "Failed to parse JWT header",
                    details = e.message
                ))
            ))
        }

        // 3. Validate typ header
        val typHeader = header["typ"]?.jsonPrimitive?.content
        if (typHeader != VerifierAttestationJwtConstants.TYP_HEADER) {
            errors.add(VerifierAttestationValidationError(
                type = ClientIdValidationErrorType.VALIDATION_ERROR,
                message = "Invalid typ header",
                details = "Expected '${VerifierAttestationJwtConstants.TYP_HEADER}', got '$typHeader'"
            ))
        }

        // 4. Decode and parse payload
        val payload = try {
            val payloadJson = jwtParts[1].decodeFrom(Encoding.BASE64URL).decodeToString()
            json.parseToJsonElement(payloadJson).jsonObject
        } catch (e: Exception) {
            return Ok(VerifyVerifierAttestationResult(
                valid = false,
                errors = listOf(VerifierAttestationValidationError(
                    type = ClientIdValidationErrorType.VALIDATION_ERROR,
                    message = "Failed to parse JWT payload",
                    details = e.message
                ))
            ))
        }

        // 5. Extract and validate claims
        val issuer = payload["iss"]?.jsonPrimitive?.content
        val subject = payload["sub"]?.jsonPrimitive?.content
        val exp = payload["exp"]?.jsonPrimitive?.longOrNull
        val iat = payload["iat"]?.jsonPrimitive?.longOrNull
        val nbf = payload["nbf"]?.jsonPrimitive?.longOrNull
        val cnf = payload["cnf"]?.jsonObject
        val cnfJwk = cnf?.get("jwk")?.jsonObject

        // 5a. Validate required claims present
        val missingClaims = mutableListOf<String>()
        if (issuer == null) missingClaims.add("iss")
        if (subject == null) missingClaims.add("sub")
        if (exp == null) missingClaims.add("exp")
        if (cnf == null) missingClaims.add("cnf")
        if (cnfJwk == null) missingClaims.add("cnf.jwk")

        if (missingClaims.isNotEmpty()) {
            errors.add(VerifierAttestationValidationError(
                type = ClientIdValidationErrorType.ATTESTATION_CLAIMS_MISSING,
                message = "Missing required claims",
                details = "Missing: ${missingClaims.joinToString()}"
            ))
        }

        // 5b. Validate issuer is trusted
        if (issuer != null && processedArgs.trustedIssuers.isNotEmpty()) {
            if (issuer !in processedArgs.trustedIssuers) {
                errors.add(VerifierAttestationValidationError(
                    type = ClientIdValidationErrorType.ATTESTATION_ISSUER_NOT_TRUSTED,
                    message = "Attestation issuer is not trusted",
                    details = "Issuer '$issuer' is not in the trusted issuers list"
                ))
            }
        }

        // 5c. Validate subject matches client_id
        if (subject != null && subject != processedArgs.expectedClientId) {
            errors.add(VerifierAttestationValidationError(
                type = ClientIdValidationErrorType.ATTESTATION_SUBJECT_MISMATCH,
                message = "Subject does not match client_id",
                details = "Expected '${processedArgs.expectedClientId}', got '$subject'"
            ))
        }

        // 5d. Validate expiration
        val currentTime = Clock.System.now().epochSeconds
        if (exp != null && exp < currentTime) {
            errors.add(VerifierAttestationValidationError(
                type = ClientIdValidationErrorType.ATTESTATION_EXPIRED,
                message = "Attestation JWT has expired",
                details = "Expiration time: $exp, current time: $currentTime"
            ))
        }

        // 5e. Validate not-before if present
        if (nbf != null && nbf > currentTime) {
            errors.add(VerifierAttestationValidationError(
                type = ClientIdValidationErrorType.VALIDATION_ERROR,
                message = "Attestation JWT is not yet valid",
                details = "Not valid before: $nbf, current time: $currentTime"
            ))
        }

        // 6. Parse cnf.jwk
        val parsedCnfJwk = if (cnfJwk != null) {
            try {
                Jwk.fromJsonObject(cnfJwk)
            } catch (e: Exception) {
                errors.add(VerifierAttestationValidationError(
                    type = ClientIdValidationErrorType.VALIDATION_ERROR,
                    message = "Failed to parse cnf.jwk",
                    details = e.message
                ))
                null
            }
        } else {
            null
        }

        // 7. Validate cnf.jwk matches JAR signer key
        val jarSignerJwk = processedArgs.jarSignerJwk
        if (parsedCnfJwk != null && jarSignerJwk != null) {
            if (!jwkThumbprintsMatch(parsedCnfJwk, jarSignerJwk)) {
                errors.add(VerifierAttestationValidationError(
                    type = ClientIdValidationErrorType.ATTESTATION_CNF_MISMATCH,
                    message = "Attestation cnf.jwk does not match JAR signer",
                    details = "The public key in the attestation does not match the key used to sign the JAR"
                ))
            }
        }

        // 8. Verify JWT signature
        val jwsVerifyResult = verifyJwsCommand.execute(
            VerifyJwsArgs(jws = JwsCompact(processedArgs.attestationJwt)))

        if (jwsVerifyResult.isErr) {
            errors.add(VerifierAttestationValidationError(
                type = ClientIdValidationErrorType.ATTESTATION_SIGNATURE_INVALID,
                message = "Failed to verify attestation JWT signature",
                details = jwsVerifyResult.error.message.defaultMessage
            ))
        } else if (!jwsVerifyResult.value.isValid) {
            errors.add(VerifierAttestationValidationError(
                type = ClientIdValidationErrorType.ATTESTATION_SIGNATURE_INVALID,
                message = "Attestation JWT signature is invalid",
                details = jwsVerifyResult.value.errorMessages.joinToString("; ")
            ))
        }

        val valid = errors.isEmpty()
        log.debug("Verifier attestation validation ${if (valid) "passed" else "failed"}: ${errors.map { it.message }}")

        return Ok(VerifyVerifierAttestationResult(
            valid = valid,
            issuer = issuer,
            subject = subject,
            cnfJwk = parsedCnfJwk,
            expirationTime = exp,
            issuedAt = iat,
            notBefore = nbf,
            errors = errors
        ))
    }

    /**
     * Compare two JWKs by their key material to determine if they represent the same key.
     *
     * For EC keys: Compare crv, x, and y parameters
     * For RSA keys: Compare n and e parameters
     * For OKP keys: Compare crv and x parameters
     */
    private fun jwkThumbprintsMatch(jwk1: Jwk, jwk2: Jwk): Boolean {
        // Compare key type
        if (jwk1.kty != jwk2.kty) return false

        return when (jwk1.kty) {
            com.sphereon.crypto.core.jose.JwaKeyType.EC -> {
                jwk1.crv == jwk2.crv && jwk1.x == jwk2.x && jwk1.y == jwk2.y
            }
            com.sphereon.crypto.core.jose.JwaKeyType.RSA -> {
                jwk1.n == jwk2.n && jwk1.e == jwk2.e
            }
            com.sphereon.crypto.core.jose.JwaKeyType.OKP -> {
                jwk1.crv == jwk2.crv && jwk1.x == jwk2.x
            }
            else -> {
                // For unknown key types (e.g., oct), try comparing by kid if present
                jwk1.kid != null && jwk1.kid == jwk2.kid
            }
        }
    }
}
