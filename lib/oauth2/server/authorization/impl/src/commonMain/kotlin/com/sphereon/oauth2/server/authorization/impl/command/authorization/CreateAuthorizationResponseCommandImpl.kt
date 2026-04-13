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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.AuthorizationResponseData
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of CreateAuthorizationResponseCommand
 *
 * Creates OAuth2 authorization endpoint success responses according to RFC 6749 Section 4.1.2.
 *
 * Response format (redirect with query parameters):
 * ```
 * HTTP/1.1 302 Found
 * Location: https://client.example.com/callback?code=AUTHORIZATION_CODE&state=STATE
 * ```
 *
 * Response parameters:
 * - code (REQUIRED) - The authorization code
 * - state (REQUIRED if present in request) - The exact state value from request
 *
 * Response modes (RFC 6749 Section 4.1.2):
 * - query (default) - Parameters in query string
 * - fragment - Parameters in fragment (for implicit flow, not used in code flow)
 * - form_post - Parameters in POST body (RFC 6749 OAuth 2.0 Form Post Response Mode)
 *
 * This is a simple command that builds the redirect URI with parameters.
 * The caller is responsible for performing the actual HTTP redirect.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAuthorizationResponseCommandImpl", exact = true)
class CreateAuthorizationResponseCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<CreateAuthorizationResponseArgs, AuthorizationResponseData>(
    commandId = CreateAuthorizationResponseCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<CreateAuthorizationResponseArgs>(),
    outputTypeToken = typeToken<AuthorizationResponseData>(),
), CreateAuthorizationResponseCommand {

    override val commandId: String get() = CreateAuthorizationResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateAuthorizationResponseArgs

    override suspend fun doExecute(
        args: CreateAuthorizationResponseArgs,
        applyDuring: (CreateAuthorizationResponseArgs) -> CreateAuthorizationResponseArgs
    ): IdkResult<AuthorizationResponseData, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.code, applied.state, applied.redirectUri, applied.responseMode)
            .mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        code: String,
        state: String?,
        redirectUri: String,
        responseMode: String
    ): IdkResult<AuthorizationResponseData, AuthorizationServerError> {
        // Build query parameters
        val parameters = buildMap<String, String> {
            put("code", code)
            if (state != null) {
                put("state", state)
            }
        }

        // Build redirect URI based on response mode
        val redirectLocation = when (responseMode.lowercase()) {
            "query" -> buildQueryRedirect(redirectUri, parameters)
            "fragment" -> buildFragmentRedirect(redirectUri, parameters)
            "form_post" -> {
                // For form_post, we return the parameters separately
                // The caller will render an HTML form that auto-submits
                redirectUri
            }
            else -> buildQueryRedirect(redirectUri, parameters)  // Default to query
        }

        // Build response
        val response = AuthorizationResponseData(
            code = code,
            state = state,
            redirectUri = redirectLocation,
            responseMode = responseMode
        )

        return Ok(response)
    }

    /**
     * Build redirect URI with query parameters
     * Example: https://client.example.com/callback?code=ABC&state=XYZ
     */
    private fun buildQueryRedirect(baseUri: String, parameters: Map<String, String>): String {
        if (parameters.isEmpty()) return baseUri

        val separator = if (baseUri.contains("?")) "&" else "?"
        val queryString = parameters.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }

        return "$baseUri$separator$queryString"
    }

    /**
     * Build redirect URI with fragment parameters
     * Example: https://client.example.com/callback#code=ABC&state=XYZ
     */
    private fun buildFragmentRedirect(baseUri: String, parameters: Map<String, String>): String {
        if (parameters.isEmpty()) return baseUri

        val fragmentString = parameters.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }

        return "$baseUri#$fragmentString"
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
