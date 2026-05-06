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
import com.sphereon.core.api.http.query.percentEncodeQueryComponent
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.command.device.DeviceVerificationApprovalHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.resolveBaseUrl
import com.sphereon.oauth2.server.authorization.impl.provider.AcceptLanguageNegotiation
import com.sphereon.oauth2.server.authorization.provider.DeviceApprovalContext
import com.sphereon.oauth2.server.authorization.provider.DeviceResultContext
import com.sphereon.oauth2.server.authorization.provider.DeviceResultOutcome
import com.sphereon.oauth2.server.authorization.provider.DeviceVerificationPageRenderer
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
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
 * HTTP shell over [DeviceVerificationPageRenderer.renderApprovalPrompt] for `GET /device/approve`.
 *
 * Flow:
 *  1. Require `user_code` query param.
 *  2. Verify the user has an active OIDC login session; if not, 302 to `/login` with a
 *     `return_url` pointing back at this endpoint with the same `user_code`.
 *  3. Look up the record. PENDING records render the approval prompt; any terminal state
 *     (APPROVED / DENIED / EXPIRED / CONSUMED) renders the matching result page directly so the
 *     user does not get a chance to retroactively flip the decision after the device polled.
 *  4. Resolve `clientName` and the requested scopes from the registry / record.
 *  5. Render.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeviceVerificationApprovalHttpEndpointCommand>())
class DeviceVerificationApprovalHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val renderer: DeviceVerificationPageRenderer,
    private val configProvider: OAuth2ServersConfigProvider,
    private val storage: DeviceAuthorizationStorage,
    private val loginSessionStore: OidcLoginSessionStore,
    private val loginSessionIdProvider: OidcLoginSessionIdProvider,
    private val clientRegistry: ClientRegistry,
) : HttpEndpointCommandAdapter(
        id = DeviceVerificationApprovalHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = DeviceVerificationApprovalHttpEndpointCommand.ENDPOINT,
    ),
    DeviceVerificationApprovalHttpEndpointCommand {
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

        val userCode =
            request.queryParameters["user_code"]?.takeIf { it.isNotBlank() }
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing required parameter: user_code", json))
        val baseUrl = request.resolveBaseUrl(configProvider)
        val locale = negotiateLocale(request.headers["accept-language"] ?: request.headers["Accept-Language"])

        if (!hasActiveLoginSession()) {
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
                val client = clientRegistry.getClient(record.clientId)
                val clientName =
                    if (client.isOk) client.value?.clientName else null
                val requestedScopes =
                    record.scope
                        ?.split(' ', '\t')
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()
                val rendered =
                    renderer.renderApprovalPrompt(
                        DeviceApprovalContext(
                            userCode = record.userCode,
                            clientId = record.clientId,
                            clientName = clientName,
                            requestedScopes = requestedScopes,
                            locale = locale,
                            baseUrl = baseUrl,
                        ),
                    )
                if (!rendered.isOk) {
                    return Ok(oauth2ErrorResponse(500, "server_error", rendered.error.message.defaultMessage, json))
                }
                val response = rendered.value
                Ok(
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

    private suspend fun hasActiveLoginSession(): Boolean {
        val sessionId = loginSessionIdProvider.currentLoginSessionId() ?: return false
        val lookup = loginSessionStore.findById(sessionId)
        return lookup.isOk && lookup.value != null
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
