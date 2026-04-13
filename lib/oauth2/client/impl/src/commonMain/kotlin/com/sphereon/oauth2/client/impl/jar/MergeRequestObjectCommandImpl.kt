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
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.MergeRequestObjectArgs
import com.sphereon.oauth2.client.command.MergeRequestObjectCommand
import com.sphereon.oauth2.client.command.MergedRequestObjectResult
import com.sphereon.oauth2.common.error.Oauth2Error
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.http.ParametersBuilder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Implementation of MergeRequestObjectCommand
 *
 * Parses JWT request objects and merges their parameters with query parameters
 * following RFC 9101 Section 3.2 rules.
 *
 * This command:
 * 1. Detects if the request object is encrypted (JWE) or just signed (JWS)
 * 2. If encrypted, decrypts using the provided key
 * 3. Verifies the signature (if verification key provided)
 * 4. Validates JWT claims (iss, aud, exp) if provided
 * 5. Extracts authorization request parameters from JWT
 * 6. Merges with query parameters per RFC 9101 rules
 */
@Inject
@SingleIn(SessionScope::class)
class MergeRequestObjectCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService,
    private val jweService: JweService,
) : TypedServiceCommandAdapter<MergeRequestObjectArgs, MergedRequestObjectResult>(
        commandId = MergeRequestObjectCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<MergeRequestObjectArgs>(),
        outputTypeToken = typeToken<MergedRequestObjectResult>(),
    ),
    MergeRequestObjectCommand {
    override val commandId: String get() = MergeRequestObjectCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is MergeRequestObjectArgs

    companion object {
        private const val JWE_PART_COUNT = 5

        // Parameters that MAY be duplicated between request object and query params per RFC 9101 Section 3.2
        // `state` is echoed in the outer URI per RFC 9101 §5; OID4VP §5.10 also keeps it
        // outside the JAR. Allow it to appear in both without flagging as a duplicate.
        private val ALLOWED_DUPLICATES = setOf("client_id", "response_type", "state")

        // JWT-specific claims that should not be part of the authorization request parameters
        private val JWT_CLAIMS = setOf("iss", "aud", "exp", "iat", "jti", "nbf")
    }

    override suspend fun doExecute(
        args: MergeRequestObjectArgs,
        applyDuring: (MergeRequestObjectArgs) -> MergeRequestObjectArgs,
    ): IdkResult<MergedRequestObjectResult, IdkError> {
        val applied = applyDuring(args)
        return mergeRequestObjectInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun mergeRequestObjectInternal(args: MergeRequestObjectArgs): IdkResult<MergedRequestObjectResult, Oauth2Error> {
        try {
            // Determine if this is a JWE (5 parts) or JWS (3 parts)
            val tokenParts = args.requestObjectJwt.split(".")
            val isEncrypted = tokenParts.size == JWE_PART_COUNT

            // Get the signed JWT (either directly or after decryption)
            val signedJwt =
                if (isEncrypted) {
                    decryptJwe(args)?.getOrElse { return Err(it) }
                        ?: return Err(
                            Oauth2Error.JarParsingFailed(
                                failureMessage = "Request object is encrypted but no decryption key provided",
                            ),
                        )
                } else {
                    args.requestObjectJwt
                }

            // Verify the signature if verification key is provided
            val verificationKey = args.verificationKey
            if (verificationKey != null) {
                val jws = JwsCompact(signedJwt)
                val verifyArgs =
                    VerifyJwsArgs(
                        jws = jws,
                        identifier = ManagedOptsKeyInfo(identifier = verificationKey),
                    )

                val verifyResult =
                    jwtService.verifyJws(verifyArgs).getOrElse { error ->
                        return Err(
                            Oauth2Error.JarParsingFailed(
                                failureMessage = "Failed to verify request object signature: ${error.message.defaultMessage}",
                                cause = error.exception,
                            ),
                        )
                    }

                if (!verifyResult.isValid) {
                    return Err(
                        Oauth2Error.JarParsingFailed(
                            failureMessage = "Request object signature verification failed",
                        ),
                    )
                }
            }

            // Parse the JWT payload using JwsUtils
            val payloadJson = parseJwtPayload(signedJwt).getOrElse { return Err(it) }

            // Validate JWT claims if validation parameters provided
            if (args.issuer != null || args.audience != null) {
                validateJwtClaims(payloadJson, args)?.let { return Err(it) }
            }

            // Extract authorization request parameters (filter out JWT-specific claims)
            val requestObjectParams = extractAuthorizationParameters(payloadJson)

            // Merge with query parameters per RFC 9101 rules
            val mergedParameters =
                mergeParameters(requestObjectParams, args.queryParameters)
                    .getOrElse { return Err(it) }

            // Convert claims to simple map for result
            val claims =
                payloadJson.mapValues { (_, value) ->
                    when (value) {
                        is JsonPrimitive -> value.contentOrNull
                        is JsonObject -> value
                        is JsonArray -> value
                        else -> null
                    }
                }

            return Ok(
                MergedRequestObjectResult(
                    mergedParameters = mergedParameters,
                    isEncrypted = isEncrypted,
                    claims = claims,
                ),
            )
        } catch (e: Exception) {
            return Err(
                Oauth2Error.JarParsingFailed(
                    failureMessage = "Failed to merge request object: ${e.message}",
                    cause = e,
                ),
            )
        }
    }

    /**
     * Decrypt JWE request object
     */
    private suspend fun decryptJwe(args: MergeRequestObjectArgs): IdkResult<String, Oauth2Error>? {
        val decryptionKey = args.decryptionKey
        if (decryptionKey == null) {
            return null
        }

        val jwe = JweCompact.parse(args.requestObjectJwt)
        val decryptArgs =
            DecryptJweArgs(
                jwe = jwe,
                decryptor = ManagedOptsKeyInfo(identifier = decryptionKey),
            )

        val decryptResult =
            jweService.decryptJwe(decryptArgs).getOrElse { error ->
                return Err(
                    Oauth2Error.JarParsingFailed(
                        failureMessage = "Failed to decrypt request object: ${error.message.defaultMessage}",
                        cause = error.exception,
                    ),
                )
            }

        return Ok(decryptResult.plaintext!!.decodeToString())
    }

    /**
     * Parse JWT payload from signed JWT string using JwsUtils
     */
    private fun parseJwtPayload(signedJwt: String): IdkResult<JsonObject, Oauth2Error> =
        try {
            // Use JwsUtils to parse the payload
            val jws = JwsCompact(signedJwt)
            val general = JwsUtils.toGeneral(jws)
            val payloadJson = JwsUtils.decodeBase64UrlToJson(general.payload)

            Ok(payloadJson)
        } catch (e: Exception) {
            Err(
                Oauth2Error.JarParsingFailed(
                    failureMessage = "Failed to parse JWT payload: ${e.message}",
                    cause = e,
                ),
            )
        }

    /**
     * Validates JWT claims per RFC 9101
     */
    private fun validateJwtClaims(
        payload: JsonObject,
        args: MergeRequestObjectArgs,
    ): Oauth2Error? {
        val now =
            kotlin.time.Clock.System
                .now()
                .epochSeconds

        // Validate iss (issuer) if provided
        if (args.issuer != null) {
            val iss = payload["iss"]?.jsonPrimitive?.content
            if (iss != args.issuer) {
                return Oauth2Error.JarParsingFailed(
                    failureMessage = "Request object issuer mismatch: expected '${args.issuer}' but got '$iss'",
                )
            }
        }

        // Validate aud (audience) if provided
        if (args.audience != null) {
            val aud = payload["aud"]?.jsonPrimitive?.content
            if (aud != args.audience) {
                return Oauth2Error.JarParsingFailed(
                    failureMessage = "Request object audience mismatch: expected '${args.audience}' but got '$aud'",
                )
            }
        }

        // Validate exp (expiration)
        val exp = payload["exp"]?.jsonPrimitive?.longOrNull
        if (exp != null && now > exp) {
            return Oauth2Error.JarParsingFailed(
                failureMessage = "Request object has expired (exp: $exp, now: $now)",
            )
        }

        return null
    }

    /**
     * Extract authorization request parameters from JWT payload
     * (filters out JWT-specific claims)
     */
    private fun extractAuthorizationParameters(payload: JsonObject): Map<String, JsonElement> = payload.filterKeys { key -> key !in JWT_CLAIMS }

    /**
     * Merge request object parameters with query parameters per RFC 9101 Section 3.2:
     * - Request Object parameters take precedence
     * - client_id and response_type MAY be present in both
     * - Other duplications MUST be rejected
     */
    private fun mergeParameters(
        requestObjectParams: Map<String, JsonElement>,
        queryParams: io.ktor.http.Parameters,
    ): IdkResult<io.ktor.http.Parameters, Oauth2Error> {
        // Check for invalid duplications
        val requestObjectKeys = requestObjectParams.keys
        val queryParamKeys = queryParams.names()
        val duplicates = requestObjectKeys.intersect(queryParamKeys) - ALLOWED_DUPLICATES

        if (duplicates.isNotEmpty()) {
            return Err(
                Oauth2Error.JarParsingFailed(
                    failureMessage = "Invalid parameter duplication between request object and query parameters: ${duplicates.joinToString(", ")}",
                ),
            )
        }

        // Build merged parameters
        val builder = ParametersBuilder()

        // Add all request object parameters first (these take precedence)
        requestObjectParams.forEach { (key, value) ->
            when (value) {
                is JsonPrimitive -> {
                    value.contentOrNull?.let { builder.append(key, it) }
                }

                is JsonArray -> {
                    // Handle arrays by adding each element
                    value.forEach { element ->
                        if (element is JsonPrimitive) {
                            element.contentOrNull?.let { builder.append(key, it) }
                        }
                    }
                }

                is JsonObject -> {
                    // Serialize complex objects as JSON strings
                    builder.append(key, value.toString())
                }
            }
        }

        // Add query parameters that are not in request object
        // For allowed duplicates (client_id, response_type), query param is already handled by request object taking precedence
        queryParams.forEach { key, values ->
            if (key !in requestObjectKeys) {
                values.forEach { value ->
                    builder.append(key, value)
                }
            }
        }

        return Ok(builder.build())
    }
}
