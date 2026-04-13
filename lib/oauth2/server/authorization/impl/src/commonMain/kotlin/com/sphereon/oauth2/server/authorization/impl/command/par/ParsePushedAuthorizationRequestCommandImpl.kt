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

package com.sphereon.oauth2.server.authorization.impl.command.par

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

private const val MIN_PKCE_LENGTH = 43
private const val MAX_PKCE_LENGTH = 128

/**
 * Implementation of ParsePushedAuthorizationRequestCommand
 *
 * Parses Pushed Authorization Requests (PAR) according to RFC 9126.
 *
 * PAR allows clients to push authorization request parameters via HTTP POST to the
 * authorization server before redirecting the user. This provides several benefits:
 * - Request integrity (parameters can't be modified in browser)
 * - Request confidentiality (parameters not visible in redirect)
 * - Support for large requests (no URL length limits)
 * - Client authentication at request time
 *
 * PAR endpoint: POST /par
 * Content-Type: application/x-www-form-urlencoded
 *
 * Request parameters (same as authorization endpoint):
 * - response_type (REQUIRED) - Must be "code"
 * - client_id (REQUIRED) - Client identifier
 * - redirect_uri (OPTIONAL) - Redirection URI
 * - scope (OPTIONAL) - Scope of access request
 * - state (RECOMMENDED) - Opaque value to prevent CSRF
 * - code_challenge (OPTIONAL) - PKCE code challenge
 * - code_challenge_method (OPTIONAL) - PKCE method
 * - Additional extension parameters
 *
 * Response:
 * - request_uri - The request URI to use in authorization request
 * - expires_in - Lifetime of the request URI in seconds (typically 90 seconds)
 *
 * This command performs the same parsing as ParseAuthorizationRequestCommand,
 * but accepts parameters from POST body instead of query string.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParsePushedAuthorizationRequestCommandImpl", exact = true)
class ParsePushedAuthorizationRequestCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<ParsePushedAuthorizationRequestArgs, AuthorizationRequestData>(
        commandId = ParsePushedAuthorizationRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ParsePushedAuthorizationRequestArgs>(),
        outputTypeToken = typeToken<AuthorizationRequestData>(),
    ),
    ParsePushedAuthorizationRequestCommand {
    override val commandId: String get() = ParsePushedAuthorizationRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ParsePushedAuthorizationRequestArgs

    override suspend fun doExecute(
        args: ParsePushedAuthorizationRequestArgs,
        applyDuring: (ParsePushedAuthorizationRequestArgs) -> ParsePushedAuthorizationRequestArgs,
    ): IdkResult<AuthorizationRequestData, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.requestBody, applied.clientAuthentication).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        requestBody: Map<String, List<String>>,
        clientAuthentication: ClientAuthenticationConfig,
    ): IdkResult<AuthorizationRequestData, AuthorizationServerError> {
        // Extract response_type (REQUIRED)
        val responseTypeStr = requestBody["response_type"]?.firstOrNull()
        if (responseTypeStr.isNullOrBlank()) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: response_type",
                ),
            )
        }

        // Parse response_type to List<ResponseType>
        val responseType =
            try {
                responseTypeStr.split(" ").map { type ->
                    when (type.lowercase()) {
                        "code" -> ResponseType.CODE

                        "token" -> ResponseType.TOKEN

                        else -> return Err(
                            AuthorizationServerError.UnsupportedResponseType(
                                responseType = type,
                            ),
                        )
                    }
                }
            } catch (expected: Exception) {
                return Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "Invalid response_type format: ${expected.message}",
                    ),
                )
            }

        // Extract client_id from authentication config
        val clientId =
            when (clientAuthentication) {
                is ClientAuthenticationConfig.Basic -> {
                    clientAuthentication.credentials.clientId
                }

                is ClientAuthenticationConfig.Post -> {
                    clientAuthentication.credentials.clientId
                }

                is ClientAuthenticationConfig.SecretJwt -> {
                    clientAuthentication.assertion.clientId
                }

                is ClientAuthenticationConfig.PrivateKeyJwt -> {
                    clientAuthentication.assertion.clientId
                }

                is ClientAuthenticationConfig.None -> {
                    clientAuthentication.clientId
                }

                is ClientAuthenticationConfig.AttestationJwt -> {
                    // For attestation JWT, client_id comes from request body
                    requestBody["client_id"]?.firstOrNull() ?: return Err(
                        AuthorizationServerError.InvalidRequest(
                            details = "Missing required parameter: client_id",
                        ),
                    )
                }

                is ClientAuthenticationConfig.Anonymous -> {
                    return Err(
                        AuthorizationServerError.InvalidRequest(
                            details = "Anonymous authentication is not allowed for PAR",
                        ),
                    )
                }
            }

        if (clientId.isBlank()) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: client_id",
                ),
            )
        }

        // Extract redirect_uri (OPTIONAL but recommended)
        val redirectUri = requestBody["redirect_uri"]?.firstOrNull()

        // Extract scope (OPTIONAL)
        val scope = requestBody["scope"]?.firstOrNull()

        // Extract state (RECOMMENDED for CSRF protection)
        val state = requestBody["state"]?.firstOrNull()

        // Extract PKCE parameters (RFC 7636)
        val codeChallenge = requestBody["code_challenge"]?.firstOrNull()
        val codeChallengeMethodStr = requestBody["code_challenge_method"]?.firstOrNull()

        // Parse code_challenge_method
        val codeChallengeMethod =
            if (codeChallengeMethodStr != null) {
                when (codeChallengeMethodStr.uppercase()) {
                    "PLAIN" -> {
                        PkceMethod.PLAIN
                    }

                    "S256" -> {
                        PkceMethod.S256
                    }

                    else -> {
                        return Err(
                            AuthorizationServerError.InvalidRequest(
                                details = "Invalid code_challenge_method: $codeChallengeMethodStr. Must be 'plain' or 'S256'",
                            ),
                        )
                    }
                }
            } else if (codeChallenge != null) {
                // Default to S256 if code_challenge is present but method is not specified
                PkceMethod.S256
            } else {
                null
            }

        // Validate PKCE parameters
        if (codeChallenge != null) {
            // Verify code_challenge format (RFC 7636 Section 4.2)
            // Must be 43-128 characters, A-Z, a-z, 0-9, -, ., _, ~
            if (codeChallenge.length !in MIN_PKCE_LENGTH..MAX_PKCE_LENGTH) {
                return Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "code_challenge must be 43-128 characters",
                    ),
                )
            }

            // Verify characters are valid (base64url)
            val validChars = Regex("^[A-Za-z0-9._~-]+$")
            if (!validChars.matches(codeChallenge)) {
                return Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "code_challenge contains invalid characters",
                    ),
                )
            }
        }

        // PAR requests should NOT contain request_uri (RFC 9126 Section 3)
        if (requestBody.containsKey("request_uri") && requestBody["request_uri"]?.firstOrNull() != null) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "request_uri parameter is not allowed in PAR requests",
                ),
            )
        }

        // Extract additional parameters for extensibility (use first value of each)
        val additionalParameters = requestBody.mapValues { (_, values) -> values.first() }

        // Build authorization request data
        // Note: redirectUri is required in AuthorizationRequestData but optional in PAR
        // We use empty string as placeholder if not provided, to be validated later
        return Ok(
            AuthorizationRequestData(
                clientId = clientId,
                redirectUri = redirectUri ?: "",
                responseType = responseType,
                scope = scope,
                state = state,
                codeChallenge = codeChallenge,
                codeChallengeMethod = codeChallengeMethod,
                requestUri = null, // Not applicable for PAR requests
                additionalParameters = additionalParameters,
            ),
        )
    }
}
