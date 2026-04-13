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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

private const val MIN_PKCE_LENGTH = 43
private const val MAX_PKCE_LENGTH = 128

/**
 * Implementation of ParseAuthorizationRequestCommand
 *
 * Parses OAuth2 authorization endpoint requests according to RFC 6749 Section 4.1.1.
 *
 * Supported response types:
 * - code (Authorization Code Flow)
 *
 * Request parameters (GET query parameters):
 * - response_type (REQUIRED) - Must be "code"
 * - client_id (REQUIRED) - Client identifier
 * - redirect_uri (OPTIONAL) - Redirection URI
 * - scope (OPTIONAL) - Scope of access request
 * - state (RECOMMENDED) - Opaque value to prevent CSRF
 * - code_challenge (OPTIONAL) - PKCE code challenge (RFC 7636)
 * - code_challenge_method (OPTIONAL) - PKCE method (plain or S256)
 * - request_uri (OPTIONAL) - PAR request URI (RFC 9126)
 *
 * This command performs basic parsing and validation, but does NOT:
 * - Verify client registration (use VerifyAuthorizationRequestCommand)
 * - Authenticate the user (use UserAuthenticationProvider)
 * - Check consent (use ConsentProvider)
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseAuthorizationRequestCommandImpl", exact = true)
class ParseAuthorizationRequestCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<ParseAuthorizationRequestArgs, AuthorizationRequestData>(
        commandId = ParseAuthorizationRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ParseAuthorizationRequestArgs>(),
        outputTypeToken = typeToken<AuthorizationRequestData>(),
    ),
    ParseAuthorizationRequestCommand {
    override val commandId: String get() = ParseAuthorizationRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ParseAuthorizationRequestArgs

    override suspend fun doExecute(
        args: ParseAuthorizationRequestArgs,
        applyDuring: (ParseAuthorizationRequestArgs) -> ParseAuthorizationRequestArgs,
    ): IdkResult<AuthorizationRequestData, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.queryParameters).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(queryParameters: Map<String, String>): IdkResult<AuthorizationRequestData, AuthorizationServerError> {
        // Extract response_type (REQUIRED)
        val responseTypeStr = queryParameters["response_type"]
        if (responseTypeStr.isNullOrBlank()) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: response_type",
                ),
            )
        }

        // Parse response types (can be space-separated for hybrid flows)
        val responseTypes =
            responseTypeStr.split(" ").mapNotNull { typeStr ->
                when (typeStr.trim().lowercase()) {
                    "code" -> ResponseType.CODE
                    "token" -> ResponseType.TOKEN
                    else -> null
                }
            }

        if (responseTypes.isEmpty()) {
            return Err(
                AuthorizationServerError.UnsupportedResponseType(
                    responseType = responseTypeStr,
                ),
            )
        }

        // Extract client_id (REQUIRED)
        val clientId = queryParameters["client_id"]
        if (clientId.isNullOrBlank()) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: client_id",
                ),
            )
        }

        // Extract redirect_uri (REQUIRED for authorization code flow)
        val redirectUri =
            queryParameters["redirect_uri"] ?: return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: redirect_uri",
                ),
            )

        // Extract scope (OPTIONAL)
        val scope = queryParameters["scope"]

        // Extract state (RECOMMENDED for CSRF protection)
        val state = queryParameters["state"]

        // Extract PKCE parameters (RFC 7636)
        val codeChallenge = queryParameters["code_challenge"]
        val codeChallengeMethodStr = queryParameters["code_challenge_method"]

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
                // Default to S256 if code_challenge is present but method is not specified (RFC 7636)
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

        // Extract PAR request_uri (RFC 9126)
        val requestUri = queryParameters["request_uri"]

        // If request_uri is present, it should be the ONLY parameter besides client_id
        // (PAR makes the authorization request via POST first)
        if (requestUri != null) {
            // Verify request_uri format (RFC 9126 Section 3)
            if (!requestUri.startsWith("urn:ietf:params:oauth:request_uri:")) {
                return Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "Invalid request_uri format. Must start with 'urn:ietf:params:oauth:request_uri:'",
                    ),
                )
            }

            // When request_uri is present, most other parameters should NOT be present
            val allowedWithRequestUri = setOf("request_uri", "client_id")
            val unexpectedParams = queryParameters.keys - allowedWithRequestUri
            if (unexpectedParams.isNotEmpty()) {
                return Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "When request_uri is present, only client_id should be provided",
                    ),
                )
            }
        }

        // Extract DPoP JKT
        val dpopJkt = queryParameters["dpop_jkt"]

        // Extract response_mode
        val responseMode = queryParameters["response_mode"]

        // Extract nonce (for OpenID Connect)
        val nonce = queryParameters["nonce"]

        // Extract authorization_details (RFC 9396 / OID4VCI)
        val authorizationDetails = queryParameters["authorization_details"]

        // Build authorization request data
        return Ok(
            AuthorizationRequestData(
                clientId = clientId,
                redirectUri = redirectUri,
                responseType = responseTypes,
                scope = scope,
                state = state,
                codeChallenge = codeChallenge,
                codeChallengeMethod = codeChallengeMethod,
                dpopJkt = dpopJkt,
                responseMode = responseMode,
                nonce = nonce,
                requestUri = requestUri,
                additionalParameters =
                    buildMap {
                        authorizationDetails?.let { put("authorization_details", it) }
                    },
            ),
        )
    }
}
