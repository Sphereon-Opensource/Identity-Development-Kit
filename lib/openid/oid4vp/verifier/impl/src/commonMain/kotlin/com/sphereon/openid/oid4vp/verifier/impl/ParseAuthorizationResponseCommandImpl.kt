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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.jarm.JarmMode
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseArgs
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseCommand
import com.sphereon.openid.oid4vp.common.VpToken
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseCommandService
import com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json

/**
 * Implementation of ParseAuthorizationResponseCommand for OpenID4VP RP (Verifier).
 *
 * Parses authorization responses containing:
 * - vp_token: Self-descriptive verifiable presentations (NOT presentation_submission!)
 * - state: Optional request correlation value
 *
 * OpenID4VP 1.0 Final Response format:
 * - vp_token can be a single string or JSON array of strings
 * - Each presentation is self-descriptive (SD-JWT, mDoc, JWT VP)
 * - NO presentation_submission (that's from DIF PE, not used in OID4VP 1.0 Final)
 *
 * JARM (direct_post.jwt) support:
 * - When "response" parameter is present, it's a JWT-secured response (JARM)
 * - JWT is verified/decrypted using VerifyJarmAuthorizationResponseCommand
 * - vp_token and state are extracted from JWT claims
 *
 * Reference: OpenID4VP 1.0 Final Section 6 - Authorization Response
 * Reference: OpenID4VP 1.0 Final Section 8.4 - Response Mode: direct_post.jwt
 */
@Inject
@SingleIn(SessionScope::class)
class ParseAuthorizationResponseCommandImpl(
    execution: SessionExecution,
    private val verifyJarmCommand: VerifyJarmResponseCommand,
) : TypedServiceCommandAdapter<ParseAuthorizationResponseArgs, ParsedAuthorizationResponse, IdkError>(
        commandId = ParseAuthorizationResponseCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ParseAuthorizationResponseArgs>(),
        outputTypeToken = typeToken<ParsedAuthorizationResponse>(),
    ),
    ParseAuthorizationResponseCommand,
    ParseAuthorizationResponseCommandService {
    override val commandId: String get() = ParseAuthorizationResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ParseAuthorizationResponseArgs

    override suspend fun parseAuthorizationResponse(args: ParseAuthorizationResponseArgs): IdkResult<ParsedAuthorizationResponse, IdkError> = execute(args)

    override suspend fun doExecute(
        args: ParseAuthorizationResponseArgs,
        applyDuring: (ParseAuthorizationResponseArgs) -> ParseAuthorizationResponseArgs,
    ): IdkResult<ParsedAuthorizationResponse, IdkError> {
        val processedArgs = applyDuring(args)

        log.debug("Parsing OpenID4VP authorization response")

        val responseParams = processedArgs.responseParams

        // Check for error response first
        val error = responseParams["error"]
        if (error != null) {
            val errorDescription = responseParams["error_description"]
            val errorUri = responseParams["error_uri"]
            val errorMessage =
                buildString {
                    append("Authorization response error: $error")
                    if (errorDescription != null) {
                        append(" - $errorDescription")
                    }
                    if (errorUri != null) {
                        append(" (see: $errorUri)")
                    }
                }
            log.error(errorMessage)
            return Err(IdkError.fromString(message = errorMessage, code = error))
        }

        // Check for JARM response (direct_post.jwt)
        // Per RFC 9101, JARM uses "response" parameter instead of individual parameters
        val jarmResponse = responseParams["response"]
        if (jarmResponse != null) {
            return parseJarmResponse(processedArgs, jarmResponse)
        }

        // Standard response (direct_post, fragment, query)
        return parseStandardResponse(processedArgs)
    }

    /**
     * Parse the `response` parameter of a `direct_post.jwt` Authorization Response.
     *
     * Per OID4VP 1.0 final §8.3 / OID4VP 1.1 draft §8.x / HAIP 1.0 §5, the response value MUST
     * be an **unsigned, encrypted JWT** whose payload is a JSON object carrying the OID4VP
     * Authorization Response parameters (`vp_token`, `state`, …) as top-level members. The
     * spec quotes verbatim:
     *
     *   "To encrypt the Authorization Response, implementations MUST use an unsigned,
     *    encrypted JWT as described in [@!RFC7519]."
     *   "The payload of the encrypted JWT response MUST include the contents of the response
     *    as defined in (#response-parameters) as top-level JSON members."
     *
     * Therefore SIGNED and SIGNED_ENCRYPTED JARM modes are non-conformant for this response
     * mode and the verifier rejects them after the JARM lib classifies the mode. The JARM lib
     * itself remains general-purpose (other JARM callers may legitimately use signed modes).
     */
    private suspend fun parseJarmResponse(
        args: ParseAuthorizationResponseArgs,
        jarmJwt: String,
    ): IdkResult<ParsedAuthorizationResponse, IdkError> {
        log.debug("Parsing JARM authorization response")

        val verifyArgs =
            VerifyJarmResponseArgs(
                jarmJwt = jarmJwt,
                expectedAudience = args.jarmExpectedAudience,
                expectedState = args.originalRequest?.state,
                decryptionKey = args.jarmDecryptionKey,
                signerIdentifier = args.jarmSignerIdentifier,
            )

        val jarmResult =
            verifyJarmCommand.execute(verifyArgs).getOrElse { error ->
                log.error("Failed to verify JARM authorization response: ${error.message}")
                return Err(error)
            }

        if (jarmResult.mode != JarmMode.ENCRYPTED) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message =
                        "OID4VP §8.3 requires direct_post.jwt responses to be unsigned-encrypted; " +
                            "got JARM mode '${jarmResult.mode}'. Signed and signed-encrypted responses are not " +
                            "permitted by OID4VP 1.0 / 1.1 / HAIP 1.0.",
                ),
            )
        }

        val payload = jarmResult.payload
        val responseParams = payload.responseParameters

        // Extract vp_token from JARM response parameters.
        //
        // OID4VP §8.1: when DCQL was used, vp_token is a JSON object keyed by credential-query
        // id, whose values are Presentation(s) that are themselves either strings (compact
        // formats) or JSON objects (ldp_vc/ldp_vp). The JARM payload may carry vp_token as:
        //  - a JSON string member, when the wallet stringified the DCQL object before embedding
        //    it as a top-level claim, OR
        //  - the DCQL JSON object directly as a structured top-level member.
        // We canonicalize to the raw JSON text in both cases and never assume the primitive form.
        val vpTokenElement = responseParams["vp_token"]
        val vpTokenRaw =
            when (vpTokenElement) {
                null -> {
                    null
                }

                is kotlinx.serialization.json.JsonPrimitive -> {
                    if (vpTokenElement.isString) vpTokenElement.content else vpTokenElement.toString()
                }

                // JSON object (DCQL) or array — serialize the structured element back to JSON text.
                else -> {
                    Json.encodeToString(
                        kotlinx.serialization.json.JsonElement
                            .serializer(),
                        vpTokenElement
                    )
                }
            } ?: return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "JARM payload missing required 'vp_token' claim",
                ),
            )

        // Parse vp_token from JARM payload
        val vpToken =
            parseVpToken(vpTokenRaw)
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Invalid vp_token format in JARM payload",
                    ),
                )

        log.info("Parsed JARM authorization response: mode=${jarmResult.mode}, vpToken queries=${vpToken.queryIds.size}, presentations=${vpToken.presentationCount}, issuer=${payload.iss}")

        return Ok(
            ParsedAuthorizationResponse(
                vpToken = vpToken,
                state = payload.state,
                rawVpToken = vpTokenRaw,
                jarmMode = jarmResult.mode,
                jarmIssuer = payload.iss,
            ),
        )
    }

    /**
     * Parse a standard (non-JARM) authorization response.
     */
    private fun parseStandardResponse(args: ParseAuthorizationResponseArgs): IdkResult<ParsedAuthorizationResponse, IdkError> {
        val responseParams = args.responseParams

        // Extract vp_token (REQUIRED)
        val vpTokenRaw =
            responseParams["vp_token"]
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "vp_token is required in authorization response",
                    ),
                )

        // Parse VP token (can be single string or JSON array)
        val vpToken =
            parseVpToken(vpTokenRaw)
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Invalid vp_token format",
                    ),
                )

        // Extract state (OPTIONAL but important for correlation)
        val state = responseParams["state"]

        // Validate state if original request is provided
        val originalRequest = args.originalRequest
        if (originalRequest != null) {
            val expectedState = originalRequest.state
            if (expectedState != null && state != expectedState) {
                log.warn("State mismatch: expected '$expectedState', got '$state'")
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "State mismatch: response state does not match request state",
                    ),
                )
            }
        }

        log.info("Parsed authorization response: vpToken queries=${vpToken.queryIds.size}, presentations=${vpToken.presentationCount}, state=${state != null}")

        return Ok(
            ParsedAuthorizationResponse(
                vpToken = vpToken,
                state = state,
                rawVpToken = vpTokenRaw,
            ),
        )
    }

    /**
     * Parse vp_token from string.
     *
     * OpenID4VP 1.0 Final DCQL format:
     * - vp_token is a JSON object where keys are credential query IDs
     * - Every value is an array of one or more Presentations
     *
     * Example:
     * ```json
     * {
     *   "driver_license_query": ["eyJhbGc..."],
     *   "employment_query": ["eyJhbGc...", "eyJhbGc..."]
     * }
     * ```
     */
    private fun parseVpToken(vpTokenRaw: String): VpToken? =
        try {
            val trimmed = vpTokenRaw.trim()

            // DCQL format: vp_token is always a JSON object
            if (trimmed.startsWith("{")) {
                // Parse as JSON object (DCQL format)
                val jsonElement = Json.parseToJsonElement(trimmed)
                VpToken.fromJson(jsonElement)
            } else {
                log.error("vp_token must be a JSON object (DCQL format)")
                null
            }
        } catch (expected: Exception) {
            log.error("Failed to parse vp_token: ${expected.message}")
            null
        }
}
