/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.oauth2.server.authorization.impl.http.command.login

import com.sphereon.conf.theme.core.model.AssetElementValue
import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.model.ResolvedFeature
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.resolve.FeatureResolver
import com.sphereon.conf.theme.core.resolve.ThemeResolver
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.command.login.AccountActionPageHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.ResponseCategory
import com.sphereon.oauth2.server.authorization.impl.http.newCspNonce
import com.sphereon.oauth2.server.authorization.impl.http.withSecurityHeaders
import com.sphereon.oauth2.server.authorization.impl.provider.AcceptLanguageNegotiation
import com.sphereon.oauth2.server.authorization.provider.AccountActionPageContext
import com.sphereon.oauth2.server.authorization.provider.AccountActionPageRenderer
import com.sphereon.software.registry.SoftwareInstanceRegistry
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException

/**
 * HTTP shell over [AccountActionPageRenderer] for `GET /account-action`, the landing page for
 * activation, tenant-onboarding and password-change links. This command resolves theming and a
 * locale, mints the CSP nonce, and delegates rendering, mirroring how
 * [LoginPageHttpEndpointCommandImpl] drives its renderer.
 *
 * The action token stays in the URL fragment so it never reaches access logs, which means nothing
 * here knows which action the visitor is completing. That decision belongs to the page script,
 * after it calls the resolve endpoint.
 *
 * Branding follows the hosted login page: ThemeResolver and FeatureResolver are optional and any
 * failure falls back to the neutral page.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(AccountActionPageHttpEndpointCommand.COMMAND_ID)
class AccountActionPageHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val configProvider: OAuth2ServersConfigProvider,
    private val pageRenderer: AccountActionPageRenderer,
    private val asInstanceIdProvider: OAuth2ServerInstanceIdProvider? = null,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver? = null,
    private val themeResolver: Provider<ThemeResolver>? = null,
    private val featureResolver: Provider<FeatureResolver>? = null,
    private val softwareInstanceRegistry: Provider<SoftwareInstanceRegistry>? = null,
) : HttpEndpointCommandAdapter(
        id = AccountActionPageHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = AccountActionPageHttpEndpointCommand.ENDPOINT,
    ),
    AccountActionPageHttpEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val nonce = newCspNonce()
        val tenantId =
            runCatching { execution.tenantId }
                .getOrNull()
                ?.takeIf { it.isNotBlank() && it != IdentityConstants.ANONYMOUS_TENANT_ID }
        val asInstanceId = asInstanceIdProvider?.currentAsInstanceId() ?: DEFAULT_AS_INSTANCE_ID
        val theming =
            if (configProvider.serverConfig.login.themeResolutionEnabled) {
                resolveThemingOrNeutral(tenantId, asInstanceId)
            } else {
                AccountActionTheming()
            }
        val trustedBase = baseUrlResolver?.resolveBaseUrl(request, configProvider)?.trimEnd('/').orEmpty()
        val rendered =
            pageRenderer.render(
                AccountActionPageContext(
                    asInstanceId = asInstanceId,
                    tenantId = tenantId,
                    locale =
                        negotiateLocale(
                            request.headers["accept-language"] ?: request.headers["Accept-Language"],
                        ),
                    cspNonce = nonce,
                    loginPath = loginPath(request.path),
                    tenantRootUrl = trustedBase.takeIf { it.isNotBlank() },
                    showWebAuthn = configProvider.serverConfig.webAuthn.enabled,
                    organizationName = theming.light?.branding?.appName,
                    resolvedThemeLight = theming.light,
                    resolvedThemeDark = theming.dark,
                    loginFeature = theming.loginFeature,
                    loginFeatureDark = theming.loginFeatureDark,
                ),
            )
        if (!rendered.isOk) return Err(rendered.error)
        val page = rendered.value
        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers = mapOf(
                    "Content-Type" to page.contentType,
                    "Cache-Control" to "no-store",
                ),
                body = page.html,
            ).withSecurityHeaders(
                ResponseCategory.HTML,
                page.cspNonce ?: nonce,
                imageOrigins = crossOriginImageOrigins(theming, trustedBase) + page.imageOrigins,
            ),
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun resolveThemingOrNeutral(
        tenantId: String?,
        asInstanceId: String,
    ): AccountActionTheming {
        val resolverProvider = themeResolver ?: return AccountActionTheming()
        if (tenantId == null) return AccountActionTheming()
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
            AccountActionTheming(light = light, dark = dark, loginFeature = loginFeature, loginFeatureDark = loginFeatureDark)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            execution.log.logManager
                .withTag("AccountActionPageHttpEndpointCommand")
                .warn("Theme resolution failed for tenant '$tenantId'; rendering the neutral account-action page: ${e.message}")
            AccountActionTheming()
        }
    }

    private fun loginPath(requestPath: String): String {
        val normalized = requestPath.substringBefore('?').trimEnd('/')
        val prefix = normalized.substringBeforeLast("/account-action", missingDelimiterValue = "")
        return if (prefix.isBlank()) "/login" else "$prefix/login"
    }

    private fun negotiateLocale(acceptLanguage: String?): String =
        AcceptLanguageNegotiation.negotiate(
            header = acceptLanguage,
            supportedLocales = SUPPORTED_LOCALES,
            defaultLocale = DEFAULT_LOCALE,
        )

    private data class AccountActionTheming(
        val light: ResolvedTheme? = null,
        val dark: ResolvedTheme? = null,
        val loginFeature: ResolvedFeature? = null,
        val loginFeatureDark: ResolvedFeature? = null,
    )

    private fun assetUri(feature: ResolvedFeature?, elementId: String): String? =
        (feature?.elements?.get(elementId)?.value as? AssetElementValue)?.asset?.uri

    private fun crossOriginImageOrigins(
        theming: AccountActionTheming,
        trustedBase: String,
    ): Set<String> {
        val candidates =
            buildList {
                for (feature in listOfNotNull(theming.loginFeature, theming.loginFeatureDark)) {
                    for (elementId in THEMED_IMAGE_ELEMENT_IDS) {
                        assetUri(feature, elementId)?.let(::add)
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
        if (candidates.isEmpty() || trustedBase.isBlank()) return emptySet()
        val baseOrigin = httpOriginOrNull(trustedBase)
        return candidates
            .mapNotNull(::httpOriginOrNull)
            .filterTo(mutableSetOf()) { it != baseOrigin }
    }

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

    companion object {
        private const val DEFAULT_AS_INSTANCE_ID: String = "default"
        private const val DEFAULT_LOCALE: String = "en"
        private const val LOGIN_FEATURE_ID: String = "login"

        /**
         * Matches the locales the themed renderer's translation table ships, so the `lang`
         * attribute the server picks lines up with what the in-page language selector can switch
         * to.
         */
        private val SUPPORTED_LOCALES: Set<String> = setOf("en", "nl", "de", "fr", "es", "zh")
        private val THEMED_IMAGE_ELEMENT_IDS: List<String> = listOf("logo", "logoDark", "background", "favicon")
        private val HOST_PORT_PATTERN = Regex("""(?:[A-Za-z0-9.-]+|\[[0-9A-Fa-f:]+])(?::\d{1,5})?""")
    }
}
