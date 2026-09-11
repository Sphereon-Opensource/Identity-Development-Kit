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

// Role-neutral OAuth REST capability. Executable server startup remains in services-oauth2-as-rest.
package com.sphereon.oauth2.server.authorization.impl.http.command.login

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.security.ConstantTime
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand
import com.sphereon.oauth2.server.authorization.command.login.LoginCancelHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.ResponseCategory
import com.sphereon.oauth2.server.authorization.impl.http.loginCsrfCookieValue
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.parseFormBody
import com.sphereon.oauth2.server.authorization.impl.http.withSecurityHeaders
import com.sphereon.oauth2.server.authorization.impl.provider.LoginCsrfTokenizer
import com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/**
 * HTTP shell over `POST /login/cancel`. The user pressed Cancel on the AS-rendered login form.
 * Looks up the pending authorization session by `session_id`, resolves the registered
 * `redirect_uri`/`state`/`response_mode`, and 302-redirects to that URL with
 * `error=access_denied` (RFC 6749 §4.1.2.1, OIDC Core §3.1.2.6) so the relying party can
 * complete its flow gracefully.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(LoginCancelHttpEndpointCommand.COMMAND_ID)
class LoginCancelHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val pendingAuthSessionStore: PendingAuthorizationSessionStore,
    private val createAuthorizationErrorResponseCommand: CreateAuthorizationErrorResponseCommand,
    private val csrfTokenizer: LoginCsrfTokenizer,
) : HttpEndpointCommandAdapter(
        id = LoginCancelHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = LoginCancelHttpEndpointCommand.ENDPOINT,
    ),
    LoginCancelHttpEndpointCommand {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

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
                    "POST /login/cancel requires Content-Type: application/x-www-form-urlencoded",
                    json,
                ),
            )
        }
        val form =
            parseFormBody(request.body)
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing or invalid request body", json))
        val sessionId =
            form["session_id"]?.firstOrNull()
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing session_id in form body", json))

        // Same CSRF defense as the login submit endpoint. A malicious site cannot fabricate
        // an "/login/cancel" POST that aborts a victim's pending auth flow without the
        // browser's `oidc_login_csrf` cookie AND a valid `session_code` from the GET render.
        val tabId =
            form["tab_id"]?.firstOrNull()
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing CSRF parameters", json))
        val sessionCode =
            form["session_code"]?.firstOrNull()
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing CSRF parameters", json))
        val cookieTabId =
            request.loginCsrfCookieValue()
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing CSRF parameters", json))
        if (!ConstantTime.equalsCT(tabId, cookieTabId) || !csrfTokenizer.verify(sessionId, tabId, sessionCode)) {
            return Ok(oauth2ErrorResponse(400, "invalid_request", "CSRF verification failed", json))
        }

        val pending =
            pendingAuthSessionStore.findById(sessionId).getOrElse {
                return Ok(oauth2ErrorResponse(500, "server_error", "Failed to load pending session", json))
            } ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Pending authorization session not found", json))

        val errorResponse =
            createAuthorizationErrorResponseCommand
                .execute(
                    CreateAuthorizationErrorResponseArgs(
                        error = "access_denied",
                        errorDescription = "The end-user denied the authorization request.",
                        state = pending.state,
                        redirectUri = pending.redirectUri,
                        responseMode = pending.responseMode,
                        clientId = pending.clientId,
                    ),
                ).getOrElse { error ->
                    return Ok(oauth2ErrorResponse(500, "server_error", error.message.defaultMessage, json))
                }

        // Pending session has fulfilled its purpose — drop it so a follow-up cancel doesn't
        // double-redirect. Best-effort: a remove failure here doesn't change correctness.
        pendingAuthSessionStore.remove(sessionId)

        val formPostHtml = errorResponse.formPostHtml
        return if (formPostHtml != null) {
            Ok(
                GenericHttpResponse(
                    statusCode = 200,
                    headers = mapOf("Content-Type" to "text/html; charset=utf-8", "Cache-Control" to "no-store"),
                    body = formPostHtml,
                ).withSecurityHeaders(ResponseCategory.HTML),
            )
        } else {
            Ok(
                GenericHttpResponse(
                    statusCode = 302,
                    headers = mapOf("Location" to errorResponse.redirectUri, "Cache-Control" to "no-store"),
                    body = "",
                ).withSecurityHeaders(ResponseCategory.REST),
            )
        }
    }
}
