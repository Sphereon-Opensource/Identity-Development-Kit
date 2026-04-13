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

package com.sphereon.oauth2.common.jarm

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jwe.DecryptJweArgs
import com.sphereon.crypto.jose.jwe.JweCompact
import com.sphereon.crypto.jose.jwe.JweService
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.VerifyJwsArgs
import com.sphereon.di.session.SessionScope
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of VerifyJarmResponseCommand.
 *
 * Verifies and decodes JARM (JWT Secured Authorization Response) for OAuth 2.0.
 *
 * Reference: RFC 9101 - JWT Secured Authorization Response Mode for OAuth 2.0
 *
 * Detection logic:
 * 1. Check if input is a JWE (5 parts separated by '.') - decrypt first
 * 2. Check if decrypted content or input is a JWS (3 parts separated by '.') - verify signature
 * 3. Extract and validate JARM claims
 */
@Inject
@SingleIn(SessionScope::class)
class VerifyJarmResponseCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService,
    private val jweService: JweService,
) : TypedServiceCommandAdapter<VerifyJarmResponseArgs, JarmVerificationResult>(
    commandId = VerifyJarmResponseCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<VerifyJarmResponseArgs>(),
    outputTypeToken = typeToken<JarmVerificationResult>(),
), VerifyJarmResponseCommand {

    override val commandId: String get() = VerifyJarmResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyJarmResponseArgs

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Standard JWT claims that are not response parameters
    private val standardClaims = setOf("iss", "aud", "exp", "iat", "nbf", "jti", "sub")

    override suspend fun doExecute(
        args: VerifyJarmResponseArgs,
        applyDuring: (VerifyJarmResponseArgs) -> VerifyJarmResponseArgs
    ): IdkResult<JarmVerificationResult, IdkError> {
        val processedArgs = applyDuring(args)
        val jarmJwt = processedArgs.jarmJwt.trim()

        if (jarmJwt.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "JARM JWT cannot be blank"
            ))
        }

        log.debug("Verifying JARM response")

        // Detect JWT type based on number of parts
        val parts = jarmJwt.split(".")
        val isJwe = parts.size == 5
        val isJws = parts.size == 3

        if (!isJwe && !isJws) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Invalid JARM JWT format: expected 3 parts (JWS) or 5 parts (JWE), got ${parts.size}"
            ))
        }

        var decrypted = false
        var signatureVerified = false
        var mode: JarmMode
        var payloadJson: JsonObject

        if (isJwe) {
            // Decrypt JWE first
            log.debug("JARM response is encrypted (JWE), attempting decryption")

            if (processedArgs.decryptionKey == null) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Decryption key is required for encrypted JARM response"
                ))
            }

            val decryptArgs = DecryptJweArgs(
                jwe = JweCompact.parse(jarmJwt),
                decryptor = processedArgs.decryptionKey
            )

            val decryptResult = jweService.decryptJwe(decryptArgs).getOrElse { error ->
                log.error("Failed to decrypt JARM JWE: ${error.message}")
                return Err(IdkError.fromString(
                    message = "Failed to decrypt JARM response: ${error.message}",
                    code = "JARM_DECRYPTION_FAILED"
                ))
            }

            decrypted = true
            val decryptedContent = decryptResult.plaintext?.decodeToString()
                ?: return Err(IdkError.fromString(
                    message = "JWE decryption returned empty plaintext",
                    code = "JARM_DECRYPTION_EMPTY"
                ))

            // Check if decrypted content is a nested JWS
            val decryptedParts = decryptedContent.split(".")
            if (decryptedParts.size == 3) {
                // Nested JWT: signed then encrypted
                log.debug("JARM response contains nested JWS, verifying signature")
                mode = JarmMode.SIGNED_ENCRYPTED

                val verifyResult = verifyJws(decryptedContent, processedArgs)
                payloadJson = verifyResult.getOrElse { error -> return Err(error) }
                signatureVerified = true
            } else {
                // Encrypted only
                mode = JarmMode.ENCRYPTED
                payloadJson = try {
                    json.parseToJsonElement(decryptedContent) as? JsonObject
                        ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Decrypted JARM content is not a JSON object"
                        ))
                } catch (e: Exception) {
                    return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Failed to parse decrypted JARM content as JSON: ${e.message}"
                    ))
                }
            }
        } else {
            // JWS only
            log.debug("JARM response is signed only (JWS), verifying signature")
            mode = JarmMode.SIGNED

            val verifyResult = verifyJws(jarmJwt, processedArgs)
            payloadJson = verifyResult.getOrElse { error -> return Err(error) }
            signatureVerified = true
        }

        // Parse and validate JARM payload
        val payload = parseJarmPayload(payloadJson).getOrElse { error -> return Err(error) }

        // Validate claims
        val validationResult = validateJarmClaims(payload, processedArgs)
        if (validationResult != null) {
            return Err(validationResult)
        }

        log.info("Successfully verified JARM response (mode: $mode, signed: $signatureVerified, decrypted: $decrypted)")

        return Ok(JarmVerificationResult(
            payload = payload,
            mode = mode,
            signatureVerified = signatureVerified,
            decrypted = decrypted
        ))
    }

    /**
     * Verifies a JWS and returns the payload as JsonObject.
     */
    private suspend fun verifyJws(
        jwsCompact: String,
        args: VerifyJarmResponseArgs
    ): IdkResult<JsonObject, IdkError> {
        val verifyArgs = VerifyJwsArgs(
            jws = JwsCompact(jwsCompact),
            identifier = args.signerIdentifier
        )

        val verifyResult = jwtService.verifyJws(verifyArgs).getOrElse { error ->
            log.error("Failed to verify JARM JWS signature: ${error.message}")
            return Err(IdkError.fromString(
                message = "Failed to verify JARM signature: ${error.message}",
                code = "JARM_SIGNATURE_INVALID"
            ))
        }

        if (!verifyResult.isValid) {
            return Err(IdkError.fromString(
                message = "JARM signature verification failed: ${verifyResult.errorMessages.joinToString(", ")}",
                code = "JARM_SIGNATURE_INVALID"
            ))
        }

        // Extract payload from the verification result
        // The payload is base64url-encoded in the JWS
        val payloadBase64 = verifyResult.jws.payload
        
        // Decode the base64url payload
        val payloadJson = try {
            val payloadBytes = payloadBase64.decodeFrom(Encoding.BASE64URL)
            val payloadString = payloadBytes.decodeToString()
            json.parseToJsonElement(payloadString) as? JsonObject
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "JWS payload is not a JSON object"
                ))
        } catch (e: Exception) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Failed to parse JWS payload: ${e.message}"
            ))
        }

        return Ok(payloadJson)
    }

    /**
     * Parses the JARM payload from JsonObject.
     *
     * Extracts standard JWT claims and separates authorization response parameters.
     */
    private fun parseJarmPayload(jsonObject: JsonObject): IdkResult<JarmResponsePayload, IdkError> {
        val iss = jsonObject["iss"]?.jsonPrimitive?.contentOrNull
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "JARM payload missing required 'iss' claim"
            ))

        val aud = jsonObject["aud"]?.jsonPrimitive?.contentOrNull
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "JARM payload missing required 'aud' claim"
            ))

        val exp = jsonObject["exp"]?.jsonPrimitive?.longOrNull
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "JARM payload missing required 'exp' claim"
            ))

        val iat = jsonObject["iat"]?.jsonPrimitive?.longOrNull
        val state = jsonObject["state"]?.jsonPrimitive?.contentOrNull

        // Extract response parameters (all claims that are not standard JWT claims)
        val responseParameters = buildJsonObject {
            jsonObject.forEach { (key, value) ->
                if (key !in standardClaims && key != "state") {
                    put(key, value)
                }
            }
        }

        return Ok(JarmResponsePayload(
            iss = iss,
            aud = aud,
            exp = exp,
            iat = iat,
            state = state,
            responseParameters = responseParameters
        ))
    }

    /**
     * Validates JARM claims against expected values.
     */
    private fun validateJarmClaims(
        payload: JarmResponsePayload,
        args: VerifyJarmResponseArgs
    ): IdkError? {
        // Validate expiration
        val now = Clock.System.now().epochSeconds
        if (payload.exp <= now) {
            return IdkError.fromString(
                message = "JARM response has expired (exp: ${payload.exp}, now: $now)",
                code = "JARM_EXPIRED"
            )
        }

        // Validate audience if expected
        if (args.expectedAudience != null && payload.aud != args.expectedAudience) {
            return IdkError.fromString(
                message = "JARM audience mismatch: expected '${args.expectedAudience}', got '${payload.aud}'",
                code = "JARM_AUDIENCE_MISMATCH"
            )
        }

        // Validate state if expected
        if (args.expectedState != null && payload.state != args.expectedState) {
            return IdkError.fromString(
                message = "JARM state mismatch: expected '${args.expectedState}', got '${payload.state}'",
                code = "JARM_STATE_MISMATCH"
            )
        }

        return null
    }
}
