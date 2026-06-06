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
import com.sphereon.oauth2.server.authorization.command.device.DeviceVerificationSubmitHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.parseFormBody
import com.sphereon.oauth2.server.authorization.impl.provider.AcceptLanguageNegotiation
import com.sphereon.oauth2.server.authorization.provider.DeviceEntryContext
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
 * HTTP shell over the device verification entry form's `POST /device`. Parses the submitted
 * `user_code` from the form body, normalises it (uppercased, optional dash inserted between the
 * two 4-character groups so the user can paste either `XXXX-XXXX` or `XXXXXXXX`), looks up the
 * record on [DeviceAuthorizationStorage], and routes to the next surface:
 *  - record missing -> re-render the entry form with an "Invalid code" banner.
 *  - record EXPIRED / DENIED / CONSUMED -> render the result page with the matching outcome.
 *  - record PENDING + no active OIDC login session -> 302 to `/login` with a `return_url`
 *    pointing back at `GET /device/approve?user_code=<>` so the user resumes after login.
 *  - record PENDING + active login session -> 302 to `GET /device/approve?user_code=<>` directly.
 *  - record APPROVED -> render the result page with [DeviceResultOutcome.APPROVED] (the user
 *    already approved on a prior tab; no need to re-prompt).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeviceVerificationSubmitHttpEndpointCommand>())
class DeviceVerificationSubmitHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val renderer: DeviceVerificationPageRenderer,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver,
    private val storage: DeviceAuthorizationStorage,
    private val loginSessionStore: OidcLoginSessionStore,
    private val loginSessionIdProvider: OidcLoginSessionIdProvider,
) : HttpEndpointCommandAdapter(
        id = DeviceVerificationSubmitHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = DeviceVerificationSubmitHttpEndpointCommand.ENDPOINT,
    ),
    DeviceVerificationSubmitHttpEndpointCommand {
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
                    "POST /device requires Content-Type: application/x-www-form-urlencoded",
                    json,
                ),
            )
        }
        val form =
            parseFormBody(request.body)
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing or invalid request body", json))

        val rawUserCode = form["user_code"]?.firstOrNull().orEmpty()
        val baseUrl = baseUrlResolver.resolveBaseUrl(request, configProvider)
        val locale = negotiateLocale(request.headers["accept-language"] ?: request.headers["Accept-Language"])
        val normalised = normaliseUserCode(rawUserCode)

        if (normalised == null) {
            return renderEntryFormWithError(locale, baseUrl, rawUserCode)
        }

        val lookup = storage.findByUserCode(normalised)
        if (lookup.isErr) {
            return Ok(oauth2ErrorResponse(500, "server_error", lookup.error.details, json))
        }
        val record =
            lookup.value
                ?: return renderEntryFormWithError(locale, baseUrl, normalised)

        return when (record.state) {
            DeviceAuthorizationState.PENDING -> {
                if (hasActiveLoginSession()) {
                    redirectTo("/device/approve?user_code=${percentEncodeQueryComponent(record.userCode)}")
                } else {
                    val approveUrl = "/device/approve?user_code=${percentEncodeQueryComponent(record.userCode)}"
                    val loginUrl = "/login?return_url=${percentEncodeQueryComponent(approveUrl)}"
                    redirectTo(loginUrl)
                }
            }

            DeviceAuthorizationState.APPROVED -> {
                renderResult(DeviceResultOutcome.APPROVED, locale, baseUrl)
            }

            DeviceAuthorizationState.DENIED -> {
                renderResult(DeviceResultOutcome.DENIED, locale, baseUrl)
            }

            DeviceAuthorizationState.EXPIRED -> {
                renderResult(DeviceResultOutcome.EXPIRED, locale, baseUrl)
            }

            DeviceAuthorizationState.CONSUMED -> {
                renderResult(DeviceResultOutcome.APPROVED, locale, baseUrl)
            }
        }
    }

    private suspend fun hasActiveLoginSession(): Boolean {
        val sessionId = loginSessionIdProvider.currentLoginSessionId() ?: return false
        val lookup = loginSessionStore.findById(sessionId)
        return lookup.isOk && lookup.value != null
    }

    private suspend fun renderEntryFormWithError(
        locale: String,
        baseUrl: String,
        prefilled: String,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val rendered =
            renderer.renderEntryForm(
                DeviceEntryContext(
                    prefilledUserCode = prefilled.takeIf { it.isNotBlank() },
                    errorMessage = INVALID_CODE_MESSAGE,
                    locale = locale,
                    baseUrl = baseUrl,
                ),
            )
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

    private fun redirectTo(location: String): IdkResult<GenericHttpResponse, IdkError> =
        Ok(
            GenericHttpResponse(
                statusCode = 302,
                headers = mapOf("Location" to location, "Cache-Control" to "no-store"),
                body = "",
            ),
        )

    private fun negotiateLocale(acceptLanguage: String?): String =
        AcceptLanguageNegotiation.negotiate(
            header = acceptLanguage,
            supportedLocales = SUPPORTED_LOCALES,
            defaultLocale = DEFAULT_LOCALE,
        )

    /**
     * Accept either `XXXX-XXXX` or `XXXXXXXX` from the user, uppercase, and re-insert the dash to
     * match the storage key format ([com.sphereon.oauth2.server.authorization.command.device.IssueDeviceAuthorizationCommand]
     * issues codes as `XXXX-XXXX`). Any other shape returns `null` so the caller surfaces the
     * "invalid code" banner.
     */
    private fun normaliseUserCode(raw: String): String? {
        val cleaned =
            raw
                .trim()
                .uppercase()
                .replace(" ", "")
                .filter { it != ' ' }
        if (cleaned.isEmpty()) return null
        val withoutDash = cleaned.replace("-", "")
        if (withoutDash.length != USER_CODE_LENGTH) return null
        if (!withoutDash.all { it.isLetterOrDigit() }) return null
        return withoutDash.substring(0, USER_CODE_GROUP_SIZE) + "-" + withoutDash.substring(USER_CODE_GROUP_SIZE)
    }

    companion object {
        private const val DEFAULT_LOCALE: String = "en"
        private val SUPPORTED_LOCALES: Set<String> = setOf("en", "nl")
        private const val INVALID_CODE_MESSAGE: String = "Invalid code. Please check the code displayed on your device."
        private const val USER_CODE_LENGTH: Int = 8
        private const val USER_CODE_GROUP_SIZE: Int = 4
    }
}
