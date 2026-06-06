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
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersArgs
import com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersCommand
import com.sphereon.oauth2.server.authorization.command.login.LoginPageHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.ResponseCategory
import com.sphereon.oauth2.server.authorization.impl.http.effectiveScheme
import com.sphereon.oauth2.server.authorization.impl.http.loginCsrfCookieHeader
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.withSecurityHeaders
import com.sphereon.oauth2.server.authorization.impl.provider.AcceptLanguageNegotiation
import com.sphereon.oauth2.server.authorization.impl.provider.LoginCsrfTokenizer
import com.sphereon.oauth2.server.authorization.provider.FederationLoginOption
import com.sphereon.oauth2.server.authorization.provider.LoginPageContext
import com.sphereon.oauth2.server.authorization.provider.LoginPageRenderer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/**
 * HTTP shell over [LoginPageRenderer] for `GET /login`. Reads `session_id`, optional `return_url`,
 * optional `error`, optional `login_hint` from query parameters; negotiates a locale from the
 * `Accept-Language` header per RFC 7231 §5.3.5 (q-value sorted, `q=0` rejections honoured,
 * language-tag prefix matching); calls the renderer; and wraps the result in a 200 response with
 * `Cache-Control: no-store` so the page is never cached in shared proxies (it carries a session id
 * and may carry a flash error).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<LoginPageHttpEndpointCommand>())
class LoginPageHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val loginPageRenderer: LoginPageRenderer,
    private val asInstanceIdProvider: OAuth2ServerInstanceIdProvider,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver,
    private val csrfTokenizer: LoginCsrfTokenizer,
    private val listEnabledFederationProvidersCommand: ListEnabledFederationProvidersCommand,
) : HttpEndpointCommandAdapter(
        id = LoginPageHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = LoginPageHttpEndpointCommand.ENDPOINT,
    ),
    LoginPageHttpEndpointCommand {
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

        @Suppress("UNCHECKED_CAST")
        val params = request.queryParameters.filterValues { it != null } as Map<String, String>
        val sessionId =
            params["session_id"]
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing session_id parameter", json))
        val baseUrl = baseUrlResolver.resolveBaseUrl(request, configProvider)
        val returnUrl = params["return_url"] ?: "$baseUrl/authorize/callback?session_id=$sessionId"
        val errorParam = params["error"]
        val errorMessage = if (errorParam == "invalid_credentials") errorParam else null
        val loginHint = params["login_hint"]
        val locale = negotiateLocale(request.headers["accept-language"] ?: request.headers["Accept-Language"])
        // Mint a fresh CSRF token tuple for THIS render. The form embeds tab_id +
        // session_code as hidden inputs; the cookie below carries tab_id so the POST handler
        // can confirm the form was generated for the same browser session as the submit.
        val csrf = csrfTokenizer.mint(sessionId)
        // Pull the enabled federation providers from the session-scoped command. Failure
        // is non-fatal: if the registry round-trip errors out, we still render the
        // password form rather than 500 the page (the user can always sign in with
        // local credentials). This matches the broader "login page must always reach
        // the user" principle that drives the no-store cache headers below.
        val federationOptions = loadFederationOptions()
        val ctx =
            LoginPageContext(
                asInstanceId = asInstanceIdProvider.currentAsInstanceId() ?: DEFAULT_AS_INSTANCE_ID,
                tenantId = null,
                sessionId = sessionId,
                returnUrl = returnUrl,
                loginHint = loginHint,
                errorMessage = errorMessage,
                locale = locale,
                tabId = csrf.tabId,
                sessionCode = csrf.sessionCode,
                notice = configProvider.serverConfig.loginNotice,
                federationOptions = federationOptions,
            )
        val rendered = loginPageRenderer.render(ctx)
        if (!rendered.isOk) {
            return Ok(oauth2ErrorResponse(500, "server_error", rendered.error.message.defaultMessage, json))
        }
        val response = rendered.value
        val secure = request.effectiveScheme(configProvider) == "https"
        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers =
                    mapOf(
                        "Content-Type" to response.contentType,
                        "Cache-Control" to "no-store",
                        "Pragma" to "no-cache",
                        "Set-Cookie" to loginCsrfCookieHeader(csrf.tabId, secure),
                    ),
                body = response.html,
            ).withSecurityHeaders(ResponseCategory.HTML),
        )
    }

    private fun negotiateLocale(acceptLanguage: String?): String =
        AcceptLanguageNegotiation.negotiate(
            header = acceptLanguage,
            supportedLocales = SUPPORTED_LOCALES,
            defaultLocale = DEFAULT_LOCALE,
        )

    private suspend fun loadFederationOptions(): List<FederationLoginOption> {
        val result = listEnabledFederationProvidersCommand.execute(ListEnabledFederationProvidersArgs)
        if (!result.isOk) return emptyList()
        return result.value.providers.map { cfg ->
            FederationLoginOption(id = cfg.id, displayName = cfg.name)
        }
    }

    companion object {
        private const val DEFAULT_LOCALE: String = "en"
        private const val DEFAULT_AS_INSTANCE_ID: String = "default"
        private val SUPPORTED_LOCALES: Set<String> = setOf("en", "nl")
    }
}
