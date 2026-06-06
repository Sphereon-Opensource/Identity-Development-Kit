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

package com.sphereon.oauth2.server.authorization.impl.http.command.logout

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.command.logout.EndSessionGetHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.logout.EndSessionPostHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.logout.HandleEndSessionRequestArgs
import com.sphereon.oauth2.server.authorization.command.logout.HandleEndSessionRequestCommand
import com.sphereon.oauth2.server.authorization.command.logout.LogoutOutcome
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.loginSessionCookieValue
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.parseFormBody
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/**
 * Cleared-cookie value for the OIDC login session: empty value plus `Max-Age=0` instructs
 * the user agent to delete the cookie immediately. Always set on the end-session response
 * so the next browser request to `/authorize` lands without a stale cookie regardless of
 * whether the AS managed to revoke the backing record.
 */
private const val CLEAR_OIDC_LOGIN_COOKIE: String =
    "oidc_login_sid=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0"

/**
 * Shared implementation body for the GET and POST end-session commands. Reads the standard
 * RP-Initiated Logout 1.0 §2 parameters out of the request, delegates to the orchestration
 * command, then renders the [LogoutOutcome] over HTTP. The cookie is cleared in either branch.
 */
private suspend fun handleEndSession(
    request: GenericHttpRequest,
    parameters: Map<String, String>,
    handle: HandleEndSessionRequestCommand,
    configProvider: OAuth2ServersConfigProvider,
    baseUrlResolver: OAuth2ServerBaseUrlResolver,
    json: Json,
): IdkResult<GenericHttpResponse, IdkError> {
    val baseUrl = baseUrlResolver.resolveBaseUrl(request, configProvider)
    val args =
        HandleEndSessionRequestArgs(
            idTokenHint = parameters["id_token_hint"]?.takeIf { it.isNotBlank() },
            clientId = parameters["client_id"]?.takeIf { it.isNotBlank() },
            postLogoutRedirectUri = parameters["post_logout_redirect_uri"]?.takeIf { it.isNotBlank() },
            state = parameters["state"]?.takeIf { it.isNotBlank() },
            logoutHint = parameters["logout_hint"]?.takeIf { it.isNotBlank() },
            uiLocales = parameters["ui_locales"]?.takeIf { it.isNotBlank() },
            currentLoginSessionId = request.loginSessionCookieValue(),
            baseUrl = baseUrl,
        )
    val outcome = handle.execute(args)
    if (!outcome.isOk) {
        return Ok(
            GenericHttpResponse(
                statusCode = 500,
                headers =
                    mapOf(
                        "Content-Type" to "application/json",
                        "Cache-Control" to "no-store",
                        "Pragma" to "no-cache",
                        "Set-Cookie" to CLEAR_OIDC_LOGIN_COOKIE,
                    ),
                body = json.encodeToString(mapOf("error" to "server_error", "error_description" to outcome.error.message.defaultMessage)),
            ),
        )
    }
    return when (val decision = outcome.value) {
        is LogoutOutcome.Redirect -> {
            Ok(
                GenericHttpResponse(
                    statusCode = 302,
                    headers =
                        mapOf(
                            "Location" to decision.location,
                            "Cache-Control" to "no-store",
                            "Set-Cookie" to CLEAR_OIDC_LOGIN_COOKIE,
                        ),
                    body = "",
                ),
            )
        }

        is LogoutOutcome.RenderPage -> {
            Ok(
                GenericHttpResponse(
                    statusCode = 200,
                    headers =
                        mapOf(
                            "Content-Type" to decision.contentType,
                            "Cache-Control" to "no-store",
                            "Pragma" to "no-cache",
                            "Set-Cookie" to CLEAR_OIDC_LOGIN_COOKIE,
                        ),
                    body = decision.html,
                ),
            )
        }
    }
}

/**
 * `GET /logout` HTTP shell. Reads RP-Initiated Logout parameters from query string and
 * delegates to [HandleEndSessionRequestCommand].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<EndSessionGetHttpEndpointCommand>())
class EndSessionGetHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val handleEndSessionRequestCommand: HandleEndSessionRequestCommand,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver,
) : HttpEndpointCommandAdapter(
        id = EndSessionGetHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = EndSessionGetHttpEndpointCommand.ENDPOINT,
    ),
    EndSessionGetHttpEndpointCommand {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    override suspend fun supports(args: Any): Boolean =
        args is GenericHttpRequest &&
            args.method.equals("GET", ignoreCase = true) &&
            args.path == EndSessionGetHttpEndpointCommand.ENDPOINT.pathPattern

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        @Suppress("UNCHECKED_CAST")
        val parameters = request.queryParameters.filterValues { it != null } as Map<String, String>
        return handleEndSession(request, parameters, handleEndSessionRequestCommand, configProvider, baseUrlResolver, json)
    }
}

/**
 * `POST /logout` HTTP shell. Same semantics as the GET form but reads parameters from
 * `application/x-www-form-urlencoded` body. Required by RP-Initiated Logout 1.0 §2.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<EndSessionPostHttpEndpointCommand>())
class EndSessionPostHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val handleEndSessionRequestCommand: HandleEndSessionRequestCommand,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver,
) : HttpEndpointCommandAdapter(
        id = EndSessionPostHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = EndSessionPostHttpEndpointCommand.ENDPOINT,
    ),
    EndSessionPostHttpEndpointCommand {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    override suspend fun supports(args: Any): Boolean =
        args is GenericHttpRequest &&
            args.method.equals("POST", ignoreCase = true) &&
            args.path == EndSessionPostHttpEndpointCommand.ENDPOINT.pathPattern

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val contentType = MediaType.parse(request.contentType)
        if (contentType == null || !contentType.matches(MediaType.ApplicationFormUrlEncoded)) {
            return Ok(
                oauth2ErrorResponse(
                    400,
                    "invalid_request",
                    "POST /logout requires Content-Type: application/x-www-form-urlencoded",
                    json,
                ),
            )
        }
        val form =
            parseFormBody(request.body)
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing or invalid request body", json))
        val parameters = form.mapValues { (_, values) -> values.first() }
        return handleEndSession(request, parameters, handleEndSessionRequestCommand, configProvider, baseUrlResolver, json)
    }
}
