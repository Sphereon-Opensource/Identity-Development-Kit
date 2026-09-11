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

import com.sphereon.conf.theme.core.model.AssetElementValue
import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.model.ResolvedFeature
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.resolve.FeatureResolver
import com.sphereon.conf.theme.core.resolve.ThemeResolver
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.command.login.LoginPageHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.ResponseCategory
import com.sphereon.oauth2.server.authorization.impl.http.effectiveScheme
import com.sphereon.oauth2.server.authorization.impl.http.isSameOriginCallback
import com.sphereon.oauth2.server.authorization.impl.http.loginCsrfCookieHeader
import com.sphereon.oauth2.server.authorization.impl.http.loginCsrfCookiePath
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.impl.http.withSecurityHeaders
import com.sphereon.oauth2.server.authorization.impl.provider.AcceptLanguageNegotiation
import com.sphereon.oauth2.server.authorization.impl.provider.LoginCsrfTokenizer
import com.sphereon.oauth2.server.authorization.provider.FederationLoginOption
import com.sphereon.oauth2.server.authorization.provider.LoginPageContext
import com.sphereon.oauth2.server.authorization.provider.LoginPageRenderer
import com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore
import com.sphereon.software.registry.SoftwareInstanceRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
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
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(LoginPageHttpEndpointCommand.COMMAND_ID)
class LoginPageHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val loginPageRenderer: LoginPageRenderer,
    private val asInstanceIdProvider: OAuth2ServerInstanceIdProvider,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver,
    private val csrfTokenizer: LoginCsrfTokenizer,
    private val pendingAuthorizationSessionStore: PendingAuthorizationSessionStore,
    // Theming is strictly optional: assemblies without a theme resolver (or without the
    // software registry that maps the AS instance slug to its application party UUID) render
    // the neutral default page. All three are Provider-wrapped optionals per the Metro
    // optional-binding rule.
    private val themeResolver: Provider<ThemeResolver>? = null,
    private val featureResolver: Provider<FeatureResolver>? = null,
    private val softwareInstanceRegistry: Provider<SoftwareInstanceRegistry>? = null,
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
        val trustedBase = baseUrlResolver.resolveBaseUrl(request, configProvider).trimEnd('/')
        val defaultReturnUrl = "$trustedBase/authorize/callback?session_id=$sessionId"
        // `return_url` is reflected from the request. Only honor it when it is a same-origin
        // callback on the trusted base; otherwise fall back to the trusted default (the user can
        // still sign in). The form `action` never derives from this value (see formActionBase).
        val requestedReturnUrl = params["return_url"]
        val returnUrl =
            when {
                requestedReturnUrl == null -> defaultReturnUrl
                isSameOriginCallback(requestedReturnUrl, trustedBase) -> requestedReturnUrl
                else -> defaultReturnUrl
            }
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
        val loginConfig = configProvider.serverConfig.login
        val pending = pendingAuthorizationSessionStore.findById(sessionId)
        if (pending.isErr || pending.value == null) {
            return Ok(oauth2ErrorResponse(400, "invalid_request", "Unknown or expired authorization transaction", json))
        }
        val routeDecision = pending.value!!.authenticationRoute
            ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Authorization transaction has no authentication route", json))
        val federationOptions = routeDecision.eligibleBindings.map {
            FederationLoginOption(id = it.bindingId, displayName = it.displayName)
        }
        // Tenant comes from the session the tenant-resolution layer established for this request
        // (subdomain / leading path slug / JWT). No default tenant is ever substituted: when the
        // session carries no real tenant, theming is skipped and the neutral page renders.
        val tenantId =
            runCatching { execution.tenantId }
                .getOrNull()
                ?.takeIf { it.isNotBlank() && it != IdentityConstants.ANONYMOUS_TENANT_ID }
        val asInstanceId = asInstanceIdProvider.currentAsInstanceId() ?: DEFAULT_AS_INSTANCE_ID
        val theming =
            if (loginConfig.themeResolutionEnabled) {
                resolveThemingOrNeutral(tenantId, asInstanceId)
            } else {
                LoginTheming()
            }
        val ctx =
            LoginPageContext(
                asInstanceId = asInstanceId,
                tenantId = tenantId,
                sessionId = sessionId,
                returnUrl = returnUrl,
                loginHint = loginHint,
                errorMessage = errorMessage,
                locale = locale,
                tabId = csrf.tabId,
                sessionCode = csrf.sessionCode,
                notice = configProvider.serverConfig.loginNotice,
                federationOptions = federationOptions,
                formActionBase = trustedBase,
                showPasswordForm = routeDecision.localLoginAllowed,
                showWebAuthn = configProvider.serverConfig.webAuthn.enabled,
                showWallet = loginConfig.showWallet && !loginConfig.walletAuthorizationUrl.isNullOrBlank(),
                walletAuthorizationUrl = loginConfig.walletAuthorizationUrl,
                defaultMethod = loginConfig.defaultMethod,
                display = params["display"]?.takeIf { it in SUPPORTED_DISPLAY_VALUES } ?: "page",
                acrValues = params["acr_values"]?.split(' ')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty(),
                forceReauth = params["force_reauth"].toBoolean(),
                cspNonce = csrf.sessionCode,
                resolvedThemeLight = theming.light,
                resolvedThemeDark = theming.dark,
                loginFeature = theming.loginFeature,
                loginFeatureDark = theming.loginFeatureDark,
            )
        val rendered = loginPageRenderer.render(ctx)
        if (!rendered.isOk) {
            return Ok(oauth2ErrorResponse(500, "server_error", rendered.error.message.defaultMessage, json))
        }
        val response = rendered.value
        // The renderer returns the per-request CSP nonce it stamped onto its inline <style>/<script>
        // (null when the rendered page carries no inline content). Thread it into the CSP header so
        // style-src/script-src allow 'nonce-X' and the inline blocks are not blocked by the strict
        // default-src 'self' baseline. Body and header stay in lockstep because both use this value.
        val secure = request.effectiveScheme(configProvider) == "https"
        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers =
                    mapOf(
                        "Content-Type" to response.contentType,
                        "Cache-Control" to "no-store",
                        "Pragma" to "no-cache",
                        "Set-Cookie" to loginCsrfCookieHeader(csrf.tabId, secure, loginCsrfCookiePath(trustedBase)),
                    ),
                body = response.html,
            ).withSecurityHeaders(
                ResponseCategory.HTML,
                response.cspNonce,
                imageOrigins =
                    crossOriginImageOrigins(theming, trustedBase) +
                        response.imageOrigins.mapNotNull(::httpOriginOrNull),
            ),
        )
    }

    /**
     * Resolved theming inputs for one render. All fields null = neutral page (today's behavior).
     */
    private data class LoginTheming(
        val light: ResolvedTheme? = null,
        val dark: ResolvedTheme? = null,
        val loginFeature: ResolvedFeature? = null,
        val loginFeatureDark: ResolvedFeature? = null,
    )

    /**
     * Resolve LIGHT + DARK themes and the `login` feature for [tenantId] under the AS instance's
     * application identity (instance slug -> software party UUID via the software registry).
     * HIGH_CONTRAST is intentionally not resolved yet.
     *
     * Every step sits behind a failure guard: a missing resolver, a missing tenant, a registry
     * miss, or ANY exception collapses to the neutral result. The login page must never fail to
     * render because of theming.
     */
    @Suppress("TooGenericExceptionCaught") // theming is best-effort; any failure must fall back to the neutral page
    private suspend fun resolveThemingOrNeutral(
        tenantId: String?,
        asInstanceId: String,
    ): LoginTheming {
        val resolverProvider = themeResolver ?: return LoginTheming()
        if (tenantId == null) return LoginTheming()
        return try {
            val applicationId = softwareInstanceRegistry?.invoke()?.get(tenantId, asInstanceId)?.partyId
            val resolver = resolverProvider.invoke()
            val light = resolver.resolve(tenant = tenantId, variant = ThemeVariant.LIGHT, applicationId = applicationId)
            val dark = resolver.resolve(tenant = tenantId, variant = ThemeVariant.DARK, applicationId = applicationId)
            val loginFeature =
                featureResolver?.invoke()?.resolve(
                    tenant = tenantId,
                    productType = ProductType.AUTHORIZATION_SERVER,
                    featureId = LOGIN_FEATURE_ID,
                    applicationId = applicationId,
                    variant = null,
                )
            val loginFeatureDark =
                featureResolver?.invoke()?.resolve(
                    tenant = tenantId,
                    productType = ProductType.AUTHORIZATION_SERVER,
                    featureId = LOGIN_FEATURE_ID,
                    applicationId = applicationId,
                    variant = ThemeVariant.DARK,
                )
            LoginTheming(light = light, dark = dark, loginFeature = loginFeature, loginFeatureDark = loginFeatureDark)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            execution.log.logManager
                .withTag("LoginPageHttpEndpointCommand")
                .warn("Theme resolution failed for tenant '$tenantId'; rendering the neutral login page: ${e.message}")
            LoginTheming()
        }
    }

    /**
     * Origins of theme-supplied image URLs (feature logo/logoDark/background/favicon assets and
     * branding logo/favicon metadata) that are cross-origin to [trustedBase]. The HTML CSP's
     * `img-src` is extended with exactly these origins so platform-hosted assets load on a
     * tenant-subdomain AS. Relative and same-origin URLs need no CSP change.
     */
    private fun crossOriginImageOrigins(
        theming: LoginTheming,
        trustedBase: String,
    ): Set<String> {
        val candidates =
            buildList {
                for (feature in listOfNotNull(theming.loginFeature, theming.loginFeatureDark)) {
                    for (elementId in THEMED_IMAGE_ELEMENT_IDS) {
                        (feature.elements[elementId]?.value as? AssetElementValue)?.asset?.uri?.let(::add)
                    }
                }
                for (theme in listOfNotNull(theming.light, theming.dark)) {
                    theme.branding?.let { branding ->
                        branding.logoUrl?.let(::add)
                        branding.logoDarkUrl?.let(::add)
                        branding.faviconUrl?.let(::add)
                    }
                }
            }
        if (candidates.isEmpty()) return emptySet()
        val baseOrigin = httpOriginOrNull(trustedBase)
        return candidates
            .mapNotNull(::httpOriginOrNull)
            .filterTo(mutableSetOf()) { it != baseOrigin }
    }

    /**
     * `scheme://host[:port]` of an absolute http(s) URL, lowercased; null for anything else.
     * The extracted host[:port] must match a strict shape (hostname or bracketed IPv6 literal,
     * optional numeric port) because the value flows into the CSP response header: tenant-writable
     * asset URIs must never be able to smuggle CRLF, quotes, spaces, or semicolons into it.
     */
    private fun httpOriginOrNull(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null
        val hostPort =
            url
                .substring(schemeEnd + 3)
                .takeWhile { it != '/' && it != '?' && it != '#' }
        if (hostPort.isEmpty() || !HOST_PORT_PATTERN.matches(hostPort)) return null
        return "$scheme://${hostPort.lowercase()}"
    }

    private fun negotiateLocale(acceptLanguage: String?): String =
        AcceptLanguageNegotiation.negotiate(
            header = acceptLanguage,
            supportedLocales = SUPPORTED_LOCALES,
            defaultLocale = DEFAULT_LOCALE,
        )

    companion object {
        private const val DEFAULT_LOCALE: String = "en"
        private const val DEFAULT_AS_INSTANCE_ID: String = "default"
        private const val LOGIN_FEATURE_ID: String = "login"
        private val SUPPORTED_LOCALES: Set<String> = setOf("en", "nl")
        private val SUPPORTED_DISPLAY_VALUES: Set<String> = setOf("page", "popup", "touch", "wap")
        private val THEMED_IMAGE_ELEMENT_IDS: List<String> = listOf("logo", "logoDark", "background", "favicon")
        private val HOST_PORT_PATTERN = Regex("""(?:[A-Za-z0-9.-]+|\[[0-9A-Fa-f:]+])(?::\d{1,5})?""")
    }
}
