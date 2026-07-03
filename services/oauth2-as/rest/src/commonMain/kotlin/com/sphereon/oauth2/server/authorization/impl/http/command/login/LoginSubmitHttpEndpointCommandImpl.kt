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

package com.sphereon.oauth2.server.authorization.impl.http.command.login

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.query.percentEncodeQueryComponent
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.Amr
import com.sphereon.core.api.service.AuthAssuranceLevel
import com.sphereon.core.api.security.ConstantTime
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.audit.OAuth2AuditEmitter
import com.sphereon.oauth2.server.authorization.audit.OAuth2AuditEventType
import com.sphereon.oauth2.server.authorization.command.login.LoginSubmitHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.ResponseCategory
import com.sphereon.oauth2.server.authorization.impl.http.effectiveScheme
import com.sphereon.oauth2.server.authorization.impl.http.loginCsrfCookieValue
import com.sphereon.oauth2.server.authorization.impl.http.loginSessionCookieHeader
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.parseFormBody
import com.sphereon.oauth2.server.authorization.impl.http.withSecurityHeaders
import com.sphereon.oauth2.server.authorization.impl.provider.LoginCsrfTokenizer
import com.sphereon.oauth2.server.authorization.provider.AuthenticationContext
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.ClientApplicationResolver
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSession
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionStore
import com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * HTTP shell over the AS login form's `POST /login`. Parses `username` / `password` /
 * `session_id` / `return_url` from an `application/x-www-form-urlencoded` body, calls
 * [UserAuthenticationProvider.authenticateWithCredentials], and on success mints an
 * [OidcLoginSession] keyed by a CSPRNG-generated id, persists it through [OidcLoginSessionStore],
 * sets the `oidc_login_sid` cookie, and 302-redirects to the supplied `return_url` (typically
 * `/authorize/callback?session_id=<original-pending-session>`). On invalid credentials it
 * 302-redirects back to `/login?session_id=...&return_url=...&error=invalid_credentials` so the
 * renderer can display the error without a stack frame leak.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<LoginSubmitHttpEndpointCommand>())
class LoginSubmitHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val userAuthProvider: UserAuthenticationProvider,
    private val loginSessionStore: OidcLoginSessionStore,
    private val pendingAuthorizationSessionStore: PendingAuthorizationSessionStore,
    private val clientApplicationResolver: ClientApplicationResolver,
    private val secureRandom: SecureRandom,
    private val configProvider: OAuth2ServersConfigProvider,
    private val clock: Clock,
    private val auditEmitter: OAuth2AuditEmitter,
    private val csrfTokenizer: LoginCsrfTokenizer,
) : HttpEndpointCommandAdapter(
        id = LoginSubmitHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = LoginSubmitHttpEndpointCommand.ENDPOINT,
    ),
    LoginSubmitHttpEndpointCommand {
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
                    "POST /login requires Content-Type: application/x-www-form-urlencoded",
                    json,
                ),
            )
        }
        val form =
            parseFormBody(request.body)
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing or invalid request body", json))
        val username = form["username"]?.firstOrNull()
        val password = form["password"]?.firstOrNull()
        val sessionId =
            form["session_id"]?.firstOrNull()
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing session_id in form body", json))
        val returnUrl =
            form["return_url"]?.firstOrNull()
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing return_url in form body", json))

        // CSRF defense (capability-URL leak resistance). The form must carry both `tab_id`
        // and `session_code = HMAC(key, session_id || "|" || tab_id)`; we also require the
        // `oidc_login_csrf` cookie's tab_id to match the form's tab_id so an attacker who
        // crafts the POST with stolen URL params still cannot complete it without the
        // browser's own cookie. Wire-visible error is generic to avoid leaking which check
        // failed (forgery vs replay vs cookie-miss).
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
            auditEmitter.emit(
                type = OAuth2AuditEventType.LOGIN_ERROR,
                metadata = mapOf("error_subcode" to "csrf_verification_failed"),
                errorCode = "invalid_request",
            )
            return Ok(oauth2ErrorResponse(400, "invalid_request", "CSRF verification failed", json))
        }

        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            // Wire-visible message is unified to `invalid_credentials` per RFC 6749 §4.1.2.1
            // anti-enumeration; the emitted event preserves the missing-input subcode so a SIEM
            // analyst can distinguish "user typed nothing" from "user typed a wrong password".
            auditEmitter.emit(
                type = OAuth2AuditEventType.LOGIN_ERROR,
                metadata = mapOf("error_subcode" to "missing_credentials"),
                errorCode = "invalid_credentials",
            )
            return Ok(redirectBackToLoginWithError(sessionId, returnUrl))
        }

        // Load the pending authorization session (read-only: findById does NOT consume; the
        // callback flow's single-use removal stays where it is) so the provider receives the
        // application id stamped at session mint. A missing/erroring pending session is an
        // invalid login attempt, not an application-agnostic credential check.
        val pendingSession =
            pendingAuthorizationSessionStore
                .findById(sessionId)
                .getOrElse { error ->
                    auditEmitter.emit(
                        type = OAuth2AuditEventType.LOGIN_ERROR,
                        metadata = mapOf("error_subcode" to "pending_session_lookup_failed"),
                        errorCode = "invalid_request",
                        errorMessage = error.message.defaultMessage,
                    )
                    return Ok(oauth2ErrorResponse(400, "invalid_request", "Unknown or expired login session", json))
                }
                ?: run {
                    auditEmitter.emit(
                        type = OAuth2AuditEventType.LOGIN_ERROR,
                        metadata = mapOf("error_subcode" to "pending_session_not_found"),
                        errorCode = "invalid_request",
                    )
                    return Ok(oauth2ErrorResponse(400, "invalid_request", "Unknown or expired login session", json))
                }
        val applicationId =
            pendingSession.applicationId
                ?: clientApplicationResolver
                    .resolveApplicationId(clientId = pendingSession.clientId, requestHost = null)
                    .getOrElse { error ->
                        auditEmitter.emit(
                            type = OAuth2AuditEventType.LOGIN_ERROR,
                            clientId = pendingSession.clientId,
                            metadata = mapOf("error_subcode" to "application_resolution_failed"),
                            errorCode = "invalid_request",
                            errorMessage = error.message.defaultMessage,
                        )
                        return Ok(oauth2ErrorResponse(400, "invalid_request", "Could not resolve login application", json))
                    }
        val authResult =
            userAuthProvider.authenticateWithCredentials(
                UserCredentials.UsernamePassword(username = username, password = password),
                AuthenticationContext(
                    sessionId = sessionId,
                    applicationId = applicationId,
                ),
            )
        if (!authResult.isOk) {
            // Provider returned a failure result. Subject is intentionally NOT included in the
            // event because the username may not correspond to a real account (the unified-error
            // anti-enumeration posture extends to the audit trail: an attacker probing usernames
            // must not be able to enumerate via /events either).
            auditEmitter.emit(
                type = OAuth2AuditEventType.LOGIN_ERROR,
                metadata = mapOf("error_subcode" to "auth_provider_rejected"),
                errorCode = "invalid_credentials",
                errorMessage = authResult.error.message.defaultMessage,
            )
            return Ok(redirectBackToLoginWithError(sessionId, returnUrl))
        }
        val sub =
            authResult.value
                ?: run {
                    auditEmitter.emit(
                        type = OAuth2AuditEventType.LOGIN_ERROR,
                        metadata = mapOf("error_subcode" to "auth_provider_returned_null_subject"),
                        errorCode = "invalid_credentials",
                    )
                    return Ok(redirectBackToLoginWithError(sessionId, returnUrl))
                }

        val now = clock.now()
        val session = configProvider.serverConfig.session
        val loginSessionId = secureRandom.newToken()
        val record =
            OidcLoginSession(
                sessionId = loginSessionId,
                sub = sub,
                authTime = now,
                authMethod = AuthenticationMethod.PASSWORD,
                createdAt = now,
                absoluteExpiresAt = now + session.absoluteTtlSeconds.seconds,
                idleExpiresAt = now + session.idleTtlSeconds.seconds,
                acr = AuthAssuranceLevel.AAL1.acr,
                amr = listOf(Amr.PWD),
            )
        val stored = loginSessionStore.create(record)
        if (!stored.isOk) {
            // Login was credential-valid but the AS could not persist the session — surfaces
            // as a server-side outage to the operator, not a credential failure to the user.
            auditEmitter.emit(
                type = OAuth2AuditEventType.LOGIN_ERROR,
                subject = sub,
                metadata = mapOf("error_subcode" to "session_store_failure"),
                errorCode = "server_error",
                errorMessage = stored.error.message.defaultMessage,
            )
            return Ok(oauth2ErrorResponse(500, "server_error", stored.error.message.defaultMessage, json))
        }
        // Successful login: subject is known, login-session id is the cookie-bound identifier
        // used downstream for OIDC `sid` claim emission. AMR is fixed to PWD here because this
        // endpoint is the password-credential entry point; step-up authenticators and IDV-bound
        // login emit their own LOGIN events with their actual AMR / ACR.
        auditEmitter.emit(
            type = OAuth2AuditEventType.LOGIN,
            subject = sub,
            metadata =
                mapOf(
                    "amr" to "pwd",
                    "acr" to AuthAssuranceLevel.AAL1.acr,
                    "auth_method" to "PASSWORD",
                    "login_session_id" to loginSessionId,
                ),
        )
        val secure = request.effectiveScheme(configProvider) == "https"
        // The `oidc_login_csrf` cookie is `Path=/login` and gets overwritten on the next
        // GET /login (which always sets a fresh tab_id), so we don't explicitly scrub it
        // here — the GenericHttpResponse map carries one Set-Cookie value and we use it
        // for the longer-lived oidc_login_sid.
        return Ok(
            GenericHttpResponse(
                statusCode = 302,
                headers =
                    mapOf(
                        "Location" to returnUrl,
                        "Cache-Control" to "no-store",
                        "Set-Cookie" to loginSessionCookieHeader(loginSessionId, secure),
                    ),
                body = "",
            ).withSecurityHeaders(ResponseCategory.REST),
        )
    }

    private fun redirectBackToLoginWithError(
        sessionId: String,
        returnUrl: String,
    ): GenericHttpResponse {
        val encodedReturn = percentEncodeQueryComponent(returnUrl)
        val encodedSession = percentEncodeQueryComponent(sessionId)
        val location =
            "/login?session_id=$encodedSession&return_url=$encodedReturn&error=invalid_credentials"
        return GenericHttpResponse(
            statusCode = 302,
            headers = mapOf("Location" to location, "Cache-Control" to "no-store"),
            body = "",
        ).withSecurityHeaders(ResponseCategory.REST)
    }
}
