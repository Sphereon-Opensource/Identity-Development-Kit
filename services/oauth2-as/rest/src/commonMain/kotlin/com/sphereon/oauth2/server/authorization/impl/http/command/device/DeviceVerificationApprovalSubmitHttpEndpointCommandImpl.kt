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

package com.sphereon.oauth2.server.authorization.impl.http.command.device

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.query.percentEncodeQueryComponent
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.command.device.DeviceVerificationApprovalSubmitHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.parseFormBody
import com.sphereon.oauth2.server.authorization.impl.provider.AcceptLanguageNegotiation
import com.sphereon.oauth2.server.authorization.provider.DeviceResultContext
import com.sphereon.oauth2.server.authorization.provider.DeviceResultOutcome
import com.sphereon.oauth2.server.authorization.provider.DeviceVerificationPageRenderer
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationState
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationStorage
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionIdProvider
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/**
 * HTTP shell over the approval form's `POST /device/approve`. Persists the user's allow / deny
 * decision on the [DeviceAuthorizationStorage] record and renders the result page.
 *
 * Allow path:
 *  1. Verify active OIDC login session (required to read `sub`, `auth_time`, and the session id
 *     that the eventual id_token's `sid` claim binds to).
 *  2. Look up the record by `user_code`. PENDING required; any other state renders the matching
 *     result page (so a stale tab cannot retroactively flip a decision).
 *  3. Update the record with `state = APPROVED`, pin `approvedSub` / `approvedAuthTime` /
 *     `approvedSessionId` from the login session, and copy the requested scope onto
 *     `grantedScope` (Phase D does not surface a per-scope opt-out; that's a future iteration).
 *  4. Render [DeviceResultOutcome.APPROVED].
 *
 * Deny path: same lookup, update to `state = DENIED`, render [DeviceResultOutcome.DENIED].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeviceVerificationApprovalSubmitHttpEndpointCommand>())
class DeviceVerificationApprovalSubmitHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val renderer: DeviceVerificationPageRenderer,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver,
    private val storage: DeviceAuthorizationStorage,
    private val loginSessionStore: OidcLoginSessionStore,
    private val loginSessionIdProvider: OidcLoginSessionIdProvider,
) : HttpEndpointCommandAdapter(
        id = DeviceVerificationApprovalSubmitHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = DeviceVerificationApprovalSubmitHttpEndpointCommand.ENDPOINT,
    ),
    DeviceVerificationApprovalSubmitHttpEndpointCommand {
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
        if (!configProvider.serverConfig.deviceFlow.isEnabled) {
            return Ok(oauth2ErrorResponse(404, "not_found", "Device verification surface is not enabled on this server", json))
        }

        val contentType = MediaType.parse(request.contentType)
        if (contentType == null || !contentType.matches(MediaType.ApplicationFormUrlEncoded)) {
            return Ok(
                oauth2ErrorResponse(
                    400,
                    "invalid_request",
                    "POST /device/approve requires Content-Type: application/x-www-form-urlencoded",
                    json,
                ),
            )
        }

        val form =
            parseFormBody(request.body)
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing or invalid request body", json))

        val userCode =
            form["user_code"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing required form field: user_code", json))
        val action =
            form["action"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing required form field: action", json))

        val baseUrl = baseUrlResolver.resolveBaseUrl(request, configProvider)
        val locale = negotiateLocale(request.headers["accept-language"] ?: request.headers["Accept-Language"])

        val sessionId = loginSessionIdProvider.currentLoginSessionId()
        if (sessionId == null) {
            val approveUrl = "/device/approve?user_code=${percentEncodeQueryComponent(userCode)}"
            val loginUrl = "/login?return_url=${percentEncodeQueryComponent(approveUrl)}"
            return Ok(
                GenericHttpResponse(
                    statusCode = 302,
                    headers = mapOf("Location" to loginUrl, "Cache-Control" to "no-store"),
                    body = "",
                ),
            )
        }
        val sessionLookup = loginSessionStore.findById(sessionId)
        val loginSession =
            if (sessionLookup.isOk) {
                sessionLookup.value
            } else {
                return Ok(oauth2ErrorResponse(500, "server_error", sessionLookup.error.message.defaultMessage, json))
            }
        if (loginSession == null) {
            val approveUrl = "/device/approve?user_code=${percentEncodeQueryComponent(userCode)}"
            val loginUrl = "/login?return_url=${percentEncodeQueryComponent(approveUrl)}"
            return Ok(
                GenericHttpResponse(
                    statusCode = 302,
                    headers = mapOf("Location" to loginUrl, "Cache-Control" to "no-store"),
                    body = "",
                ),
            )
        }

        val lookup = storage.findByUserCode(userCode)
        if (lookup.isErr) {
            return Ok(oauth2ErrorResponse(500, "server_error", lookup.error.details, json))
        }
        val record =
            lookup.value
                ?: return renderResult(DeviceResultOutcome.INVALID_CODE, locale, baseUrl)

        return when (record.state) {
            DeviceAuthorizationState.PENDING -> {
                when (action.lowercase()) {
                    "allow" -> {
                        val updated =
                            storage.update(
                                record.copy(
                                    state = DeviceAuthorizationState.APPROVED,
                                    approvedSub = loginSession.sub,
                                    approvedAuthTime = loginSession.authTime,
                                    approvedSessionId = loginSession.sessionId,
                                    grantedScope = record.scope,
                                ),
                            )
                        if (updated.isErr) {
                            return Ok(oauth2ErrorResponse(500, "server_error", updated.error.details, json))
                        }
                        renderResult(DeviceResultOutcome.APPROVED, locale, baseUrl)
                    }

                    "deny" -> {
                        val updated = storage.update(record.copy(state = DeviceAuthorizationState.DENIED))
                        if (updated.isErr) {
                            return Ok(oauth2ErrorResponse(500, "server_error", updated.error.details, json))
                        }
                        renderResult(DeviceResultOutcome.DENIED, locale, baseUrl)
                    }

                    else -> {
                        Ok(
                            oauth2ErrorResponse(
                                400,
                                "invalid_request",
                                "Form field 'action' must be 'allow' or 'deny'",
                                json,
                            ),
                        )
                    }
                }
            }

            DeviceAuthorizationState.APPROVED, DeviceAuthorizationState.CONSUMED -> {
                renderResult(DeviceResultOutcome.APPROVED, locale, baseUrl)
            }

            DeviceAuthorizationState.DENIED -> {
                renderResult(DeviceResultOutcome.DENIED, locale, baseUrl)
            }

            DeviceAuthorizationState.EXPIRED -> {
                renderResult(DeviceResultOutcome.EXPIRED, locale, baseUrl)
            }
        }
    }

    private suspend fun renderResult(
        outcome: DeviceResultOutcome,
        locale: String,
        baseUrl: String,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val rendered = renderer.renderResultPage(DeviceResultContext(outcome = outcome, locale = locale, baseUrl = baseUrl))
        if (!rendered.isOk) {
            return Ok(oauth2ErrorResponse(500, "server_error", rendered.error.message.defaultMessage, json))
        }
        val response = rendered.value
        return Ok(
            GenericHttpResponse(
                statusCode = response.statusCode,
                headers =
                    mapOf(
                        "Content-Type" to response.contentType,
                        "Cache-Control" to "no-store",
                        "Pragma" to "no-cache",
                    ),
                body = response.html,
            ),
        )
    }

    private fun negotiateLocale(acceptLanguage: String?): String =
        AcceptLanguageNegotiation.negotiate(
            header = acceptLanguage,
            supportedLocales = SUPPORTED_LOCALES,
            defaultLocale = DEFAULT_LOCALE,
        )

    companion object {
        private const val DEFAULT_LOCALE: String = "en"
        private val SUPPORTED_LOCALES: Set<String> = setOf("en", "nl")
    }
}
