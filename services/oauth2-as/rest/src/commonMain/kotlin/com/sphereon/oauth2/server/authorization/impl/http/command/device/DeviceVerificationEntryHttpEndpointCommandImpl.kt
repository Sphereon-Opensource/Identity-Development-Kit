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
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.command.device.DeviceVerificationEntryHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.provider.AcceptLanguageNegotiation
import com.sphereon.oauth2.server.authorization.provider.DeviceEntryContext
import com.sphereon.oauth2.server.authorization.provider.DeviceVerificationPageRenderer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/**
 * HTTP shell over [DeviceVerificationPageRenderer.renderEntryForm] for `GET /device`. Reads the
 * optional `user_code` query parameter (carried over from the device's
 * `verification_uri_complete`) and renders the entry form. Always returns 200 HTML when the
 * device-flow feature policy is enabled; surfaces a 404 `not_found` error when disabled so the
 * surface stays in lock-step with `/device_authorization`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeviceVerificationEntryHttpEndpointCommand>())
class DeviceVerificationEntryHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val renderer: DeviceVerificationPageRenderer,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver,
) : HttpEndpointCommandAdapter(
        id = DeviceVerificationEntryHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = DeviceVerificationEntryHttpEndpointCommand.ENDPOINT,
    ),
    DeviceVerificationEntryHttpEndpointCommand {
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

        val userCode = request.queryParameters["user_code"]?.takeIf { it.isNotBlank() }
        val baseUrl = baseUrlResolver.resolveBaseUrl(request, configProvider)
        val locale = negotiateLocale(request.headers["accept-language"] ?: request.headers["Accept-Language"])

        val rendered =
            renderer.renderEntryForm(
                DeviceEntryContext(
                    prefilledUserCode = userCode,
                    errorMessage = null,
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
