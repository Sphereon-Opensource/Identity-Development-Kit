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

package com.sphereon.oauth2.server.authorization.impl.command.introspection

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.IntrospectionRequestData
import com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of ParseIntrospectionRequestCommand
 *
 * Parses OAuth2 token introspection requests according to RFC 7662.
 *
 * The token introspection endpoint allows authorized callers (typically resource servers)
 * to query the authorization server about the state and metadata of a token.
 *
 * Introspection endpoint: POST /introspect
 * Content-Type: application/x-www-form-urlencoded
 *
 * Request parameters:
 * - token (REQUIRED) - The token to introspect
 * - token_type_hint (OPTIONAL) - Hint about token type ("access_token" or "refresh_token")
 *
 * Response (JSON):
 * ```json
 * {
 *   "active": true,
 *   "scope": "read write",
 *   "client_id": "client123",
 *   "username": "user@example.com",
 *   "token_type": "Bearer",
 *   "exp": 1419356238,
 *   "iat": 1419350238,
 *   "sub": "user123",
 *   "aud": "https://protected.example.net/resource",
 *   "iss": "https://server.example.com/"
 * }
 * ```
 *
 * If the token is not active (expired, revoked, or invalid):
 * ```json
 * {
 *   "active": false
 * }
 * ```
 *
 * Security considerations:
 * - Client authentication is REQUIRED (RFC 7662 Section 2.1)
 * - Only authorized clients should be able to introspect tokens
 * - The response may contain sensitive information about the token
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseIntrospectionRequestCommandImpl", exact = true)
class ParseIntrospectionRequestCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<ParseIntrospectionRequestArgs, IntrospectionRequestData>(
        commandId = ParseIntrospectionRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ParseIntrospectionRequestArgs>(),
        outputTypeToken = typeToken<IntrospectionRequestData>(),
    ),
    ParseIntrospectionRequestCommand {
    override val commandId: String get() = ParseIntrospectionRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ParseIntrospectionRequestArgs

    override suspend fun doExecute(
        args: ParseIntrospectionRequestArgs,
        applyDuring: (ParseIntrospectionRequestArgs) -> ParseIntrospectionRequestArgs,
    ): IdkResult<IntrospectionRequestData, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.requestBody).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(requestBody: Map<String, List<String>>): IdkResult<IntrospectionRequestData, AuthorizationServerError> {
        // Extract token (REQUIRED)
        val token = requestBody["token"]?.firstOrNull()
        if (token.isNullOrBlank()) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: token",
                ),
            )
        }

        // Extract token_type_hint (OPTIONAL)
        val tokenTypeHint = requestBody["token_type_hint"]?.firstOrNull()

        // Validate token_type_hint if provided
        if (tokenTypeHint != null && tokenTypeHint.isNotBlank()) {
            when (tokenTypeHint) {
                "access_token", "refresh_token" -> {
                    // Valid hints
                }

                else -> {
                    // RFC 7662 Section 2.1: Unknown hints should be ignored, not rejected
                    // But we'll pass it through for the caller to handle
                }
            }
        }

        // Extract client_id from body
        val clientId =
            requestBody["client_id"]?.firstOrNull() ?: return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: client_id",
                ),
            )

        // Build introspection request data
        return Ok(
            IntrospectionRequestData(
                token = token,
                tokenTypeHint = tokenTypeHint,
                clientId = clientId,
            ),
        )
    }
}
