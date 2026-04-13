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

package com.sphereon.oauth2.client.impl.jar

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
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.ParseJarArgs
import com.sphereon.oauth2.client.command.ParseJarCommand
import com.sphereon.oauth2.client.command.ParsedJarResult
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.AuthorizationRequest
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Clock

/**
 * Implementation of ParseJarCommand
 *
 * Parses and validates a JAR (JWT-secured Authorization Request) as defined in RFC 9101.
 *
 * This command:
 * 1. Detects if the JAR is encrypted (JWE) or just signed (JWS)
 * 2. If encrypted, decrypts using the provided key
 * 3. Verifies the signature
 * 4. Validates JWT claims (iss, aud, exp)
 * 5. Extracts authorization request parameters
 */
@Inject
@SingleIn(SessionScope::class)
class ParseJarCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService,
    private val jweService: JweService,
) : TypedServiceCommandAdapter<ParseJarArgs, ParsedJarResult>(
        commandId = ParseJarCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ParseJarArgs>(),
        outputTypeToken = typeToken<ParsedJarResult>(),
    ),
    ParseJarCommand {
    override val commandId: String get() = ParseJarCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ParseJarArgs

    override suspend fun doExecute(
        args: ParseJarArgs,
        applyDuring: (ParseJarArgs) -> ParseJarArgs,
    ): IdkResult<ParsedJarResult, IdkError> {
        val applied = applyDuring(args)
        return parseJarInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun parseJarInternal(args: ParseJarArgs): IdkResult<ParsedJarResult, Oauth2Error> {
        try {
            // Determine if this is a JWE (5 parts) or JWS (3 parts)
            val tokenParts = args.jarToken.split(".")
            val isEncrypted = tokenParts.size == JWE_PART_COUNT

            // Get the signed JWT (either directly or after decryption)
            val signedJwt =
                if (isEncrypted) {
                    // Decrypt the JWE first
                    if (args.decryptionKey == null) {
                        return Err(
                            Oauth2Error.JarParsingFailed(
                                failureMessage = "JAR is encrypted but no decryption key provided",
                            ),
                        )
                    }

                    val jwe = JweCompact.parse(args.jarToken)
                    val decryptionKey = args.decryptionKey!! // Null check already done above
                    val decryptArgs =
                        DecryptJweArgs(
                            jwe = jwe,
                            decryptor = ManagedOptsKeyInfo(identifier = decryptionKey),
                        )

                    val decryptResult =
                        jweService.decryptJwe(decryptArgs).getOrElse { error ->
                            return Err(
                                Oauth2Error.JarParsingFailed(
                                    failureMessage = "Failed to decrypt JAR: ${error.message.defaultMessage}",
                                    cause = error.exception,
                                ),
                            )
                        }

                    // The plaintext should be a signed JWT
                    decryptResult.plaintext!!.decodeToString()
                } else {
                    // Already a signed JWT
                    args.jarToken
                }

            // Verify the signature
            if (args.verificationKey == null) {
                return Err(
                    Oauth2Error.JarParsingFailed(
                        failureMessage = "No verification key provided for JAR signature verification",
                    ),
                )
            }

            val jws = JwsCompact(signedJwt)
            val verificationKey = args.verificationKey!! // Null check already done above
            val verifyArgs =
                VerifyJwsArgs(
                    jws = jws,
                    identifier = ManagedOptsKeyInfo(identifier = verificationKey),
                )

            val verifyResult =
                jwtService.verifyJws(verifyArgs).getOrElse { error ->
                    return Err(
                        Oauth2Error.JarParsingFailed(
                            failureMessage = "Failed to verify JAR signature: ${error.message.defaultMessage}",
                            cause = error.exception,
                        ),
                    )
                }

            if (!verifyResult.isValid) {
                return Err(
                    Oauth2Error.JarParsingFailed(
                        failureMessage = "JAR signature verification failed",
                    ),
                )
            }

            // Parse the payload as JSON (already decoded during verification)
            val payloadJson = verifyResult.parsedPayload

            // Validate JWT claims
            validateJwtClaims(payloadJson, args)?. let { return Err(it) }

            // Extract authorization request parameters (excluding JWT-specific claims)
            val authRequestJson = buildAuthorizationRequestJson(payloadJson)

            // Parse into AuthorizationRequest
            val authorizationRequest =
                try {
                    Json.decodeFromJsonElement(AuthorizationRequest.serializer(), authRequestJson)
                } catch (expected: Exception) {
                    return Err(
                        Oauth2Error.JarParsingFailed(
                            failureMessage = "Failed to parse authorization request from JAR: ${expected.message}",
                            cause = expected,
                        ),
                    )
                }

            // Convert JsonObject to Map for additional claims
            val claims =
                payloadJson.mapValues { (_, value) ->
                    when {
                        value is JsonObject -> value
                        else -> value.jsonPrimitive.content
                    }
                }

            return Ok(
                ParsedJarResult(
                    authorizationRequest = authorizationRequest,
                    isEncrypted = isEncrypted,
                    claims = claims,
                ),
            )
        } catch (expected: Exception) {
            return Err(
                Oauth2Error.JarParsingFailed(
                    failureMessage = "JAR parsing failed: ${expected.message}",
                    cause = expected,
                ),
            )
        }
    }

    /**
     * Validates JWT claims per RFC 9101:
     * - iss: Must match expected issuer (client_id)
     * - aud: Must match expected audience (authorization server)
     * - exp: Must not be expired
     */
    private fun validateJwtClaims(
        payload: JsonObject,
        args: ParseJarArgs,
    ): Oauth2Error? {
        val now = Clock.System.now().epochSeconds

        // Validate iss (issuer)
        val iss = payload["iss"]?.jsonPrimitive?.content
        if (iss != args.issuer) {
            return Oauth2Error.JarParsingFailed(
                failureMessage = "JAR issuer mismatch: expected '${args.issuer}' but got '$iss'",
            )
        }

        // Validate aud (audience)
        val aud = payload["aud"]?.jsonPrimitive?.content
        if (aud != args.audience) {
            return Oauth2Error.JarParsingFailed(
                failureMessage = "JAR audience mismatch: expected '${args.audience}' but got '$aud'",
            )
        }

        // Validate exp (expiration)
        val exp = payload["exp"]?.jsonPrimitive?.long
        if (exp == null) {
            return Oauth2Error.JarParsingFailed(
                failureMessage = "JAR missing required 'exp' claim",
            )
        }
        if (now > exp) {
            return Oauth2Error.JarParsingFailed(
                failureMessage = "JAR has expired (exp: $exp, now: $now)",
            )
        }

        return null
    }

    /**
     * Builds authorization request JSON by filtering out JWT-specific claims
     */
    private fun buildAuthorizationRequestJson(payload: JsonObject): JsonObject {
        // JWT-specific claims that should not be part of the authorization request
        val jwtClaims = setOf("iss", "aud", "exp", "iat", "jti", "nbf")

        val filteredMap = payload.filterKeys { key -> key !in jwtClaims }

        return JsonObject(filteredMap)
    }

    companion object {
        private const val JWE_PART_COUNT = 5
    }
}
