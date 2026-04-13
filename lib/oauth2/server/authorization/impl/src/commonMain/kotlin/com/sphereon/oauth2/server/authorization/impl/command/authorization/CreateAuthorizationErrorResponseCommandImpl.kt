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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.AuthorizationErrorResponseData
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of CreateAuthorizationErrorResponseCommand
 *
 * Creates OAuth2 authorization endpoint error responses according to RFC 6749 Section 4.1.2.1.
 *
 * Error response format (redirect with query parameters):
 * ```
 * HTTP/1.1 302 Found
 * Location: https://client.example.com/callback?error=invalid_request&error_description=...&state=STATE
 * ```
 *
 * Error parameters:
 * - error (REQUIRED) - Error code
 * - error_description (OPTIONAL) - Human-readable description
 * - error_uri (OPTIONAL) - URI to error documentation
 * - state (REQUIRED if present in request) - The exact state value from request
 *
 * Standard error codes (RFC 6749 Section 4.1.2.1):
 * - invalid_request - Missing/invalid parameter
 * - unauthorized_client - Client not authorized
 * - access_denied - Resource owner denied request
 * - unsupported_response_type - Authorization server doesn't support response_type
 * - invalid_scope - Requested scope is invalid/unknown/malformed
 * - server_error - Authorization server encountered error
 * - temporarily_unavailable - Server temporarily unavailable
 *
 * IMPORTANT: Error responses SHOULD NOT be sent if:
 * - redirect_uri is missing or invalid
 * - client_id is missing or invalid
 * In these cases, display error to user directly (don't redirect)
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAuthorizationErrorResponseCommandImpl", exact = true)
class CreateAuthorizationErrorResponseCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<CreateAuthorizationErrorResponseArgs, AuthorizationErrorResponseData>(
        commandId = CreateAuthorizationErrorResponseCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateAuthorizationErrorResponseArgs>(),
        outputTypeToken = typeToken<AuthorizationErrorResponseData>(),
    ),
    CreateAuthorizationErrorResponseCommand {
    override val commandId: String get() = CreateAuthorizationErrorResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateAuthorizationErrorResponseArgs

    override suspend fun doExecute(
        args: CreateAuthorizationErrorResponseArgs,
        applyDuring: (CreateAuthorizationErrorResponseArgs) -> CreateAuthorizationErrorResponseArgs,
    ): IdkResult<AuthorizationErrorResponseData, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.error, applied.errorDescription, applied.errorUri, applied.state, applied.redirectUri)
            .mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        error: String,
        errorDescription: String?,
        errorUri: String?,
        state: String?,
        redirectUri: String,
    ): IdkResult<AuthorizationErrorResponseData, AuthorizationServerError> {
        // Build error parameters
        val parameters =
            buildMap<String, String> {
                put("error", error)
                if (errorDescription != null) {
                    put("error_description", errorDescription)
                }
                if (errorUri != null) {
                    put("error_uri", errorUri)
                }
                if (state != null) {
                    put("state", state)
                }
            }

        // Build redirect URI with error parameters (always use query mode for errors per RFC 6749)
        val redirectLocation = buildQueryRedirect(redirectUri, parameters)

        // Build error response
        val response =
            AuthorizationErrorResponseData(
                error = error,
                errorDescription = errorDescription,
                errorUri = errorUri,
                state = state,
                redirectUri = redirectLocation,
            )

        return Ok(response)
    }

    /**
     * Build redirect URI with query parameters
     * Example: https://client.example.com/callback?error=invalid_request&error_description=...&state=XYZ
     */
    private fun buildQueryRedirect(
        baseUri: String,
        parameters: Map<String, String>,
    ): String {
        if (parameters.isEmpty()) {
            return baseUri
        }

        val separator =
            if (baseUri.contains("?")) {
                "&"
            } else {
                "?"
            }
        val queryString =
            parameters.entries.joinToString("&") { (key, value) ->
                "${urlEncode(key)}=${urlEncode(value)}"
            }

        return "$baseUri$separator$queryString"
    }

    /**
     * URL encode a string
     * TODO: Use proper URL encoder from core library
     */
    private fun urlEncode(value: String): String {
        // Placeholder - need to use actual URL encoder
        // For now, basic implementation
        return value
            .replace("%", "%25")
            .replace(" ", "%20")
            .replace("!", "%21")
            .replace("#", "%23")
            .replace("$", "%24")
            .replace("&", "%26")
            .replace("'", "%27")
            .replace("(", "%28")
            .replace(")", "%29")
            .replace("*", "%2A")
            .replace("+", "%2B")
            .replace(",", "%2C")
            .replace("/", "%2F")
            .replace(":", "%3A")
            .replace(";", "%3B")
            .replace("=", "%3D")
            .replace("?", "%3F")
            .replace("@", "%40")
            .replace("[", "%5B")
            .replace("]", "%5D")
    }
}
