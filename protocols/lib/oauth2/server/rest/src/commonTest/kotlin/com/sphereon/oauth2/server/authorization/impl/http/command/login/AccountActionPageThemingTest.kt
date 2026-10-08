/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.oauth2.server.authorization.impl.http.command.login

import com.sphereon.conf.theme.core.model.BrandingMetadata
import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.model.ResolvedFeature
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.resolve.FeatureResolver
import com.sphereon.conf.theme.core.resolve.ThemeResolver
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.WebAuthnLoginConfig
import com.sphereon.oauth2.server.authorization.impl.http.DefaultOAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.command.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import com.sphereon.oauth2.server.authorization.provider.AccountActionPageContext
import com.sphereon.oauth2.server.authorization.provider.AccountActionPageRenderer
import com.sphereon.oauth2.server.authorization.provider.AccountActionPageResponse
import com.sphereon.software.registry.SoftwareInstanceRegistry
import com.sphereon.software.registry.model.SoftwareCapabilityType
import com.sphereon.software.registry.model.SoftwareInstance
import dev.zacsweers.metro.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The command is an HTTP shell: it resolves theming and a locale, mints the CSP nonce, and hands
 * everything to an [AccountActionPageRenderer]. These tests assert on the context it builds.
 * Markup is the renderer's contract and is covered by the renderer's own tests.
 */
class AccountActionPageThemingTest {
    @Test
    fun resolvedThemingAndNonceReachTheRenderer() =
        runTest {
            val renderer = RecordingRenderer()
            val response = commandWith(renderer).execute(request())

            assertTrue(response.isOk)
            assertEquals(200, response.value.statusCode)
            val ctx = assertNotNull(renderer.last, "the command must call the renderer")
            assertEquals("acme", ctx.tenantId)
            assertEquals("default", ctx.asInstanceId)
            assertEquals("Acme", ctx.organizationName)
            assertEquals("#0085CA", assertNotNull(ctx.resolvedThemeLight).tokens["color.primary"])
            assertNotNull(ctx.resolvedThemeDark)
            assertTrue(ctx.showWebAuthn, "webAuthn is enabled in this fixture")
            assertTrue(!ctx.cspNonce.isNullOrBlank(), "the renderer needs a nonce for its inline blocks")
        }

    @Test
    fun theNonceTheRendererStampedIsPinnedOnTheCspHeader() =
        runTest {
            val renderer = RecordingRenderer()
            val response = commandWith(renderer).execute(request())

            assertTrue(response.isOk)
            val nonce = assertNotNull(assertNotNull(renderer.last).cspNonce)
            val csp = response.value.headers.entries.first { it.key.equals("Content-Security-Policy", true) }.value
            assertTrue(csp.contains("script-src"), "CSP must constrain scripts")
            assertTrue(csp.contains("nonce-$nonce"), "the header must pin the nonce the page actually carries")
        }

    @Test
    fun theAccountActionResponseIsNeverCached() =
        runTest {
            val response = commandWith(RecordingRenderer()).execute(request())

            assertTrue(response.isOk)
            assertEquals("no-store", response.value.headers["Cache-Control"])
        }

    /**
     * `supports()` matches the request against `endpoint.pathPatterns`, which is `/account-action`,
     * so a prefixed path cannot be driven through `execute()` here and `doExecute` is protected.
     * This pins the unprefixed derivation. The `/as/{slug}/account-action` mount is a deployment
     * concern covered by the tenant-AS surface test.
     */
    @Test
    fun theLoginPathIsDerivedFromTheRequestPath() =
        runTest {
            val renderer = RecordingRenderer()
            commandWith(renderer).execute(request(path = "/account-action"))

            assertEquals("/login", assertNotNull(renderer.last).loginPath)
        }

    @Test
    fun theLocaleIsNegotiatedFromAcceptLanguage() =
        runTest {
            val renderer = RecordingRenderer()
            commandWith(renderer).execute(request(acceptLanguage = "nl-NL,nl;q=0.9,en;q=0.5"))

            assertEquals("nl", assertNotNull(renderer.last).locale)
        }

    @Test
    fun anUnsupportedLanguageFallsBackToEnglish() =
        runTest {
            val renderer = RecordingRenderer()
            commandWith(renderer).execute(request(acceptLanguage = "sv-SE,sv;q=0.9"))

            assertEquals("en", assertNotNull(renderer.last).locale)
        }

    @Test
    fun themeResolutionFailureStillRendersANeutralPage() =
        runTest {
            val renderer = RecordingRenderer()
            val response = commandWith(renderer, themeResolver = ThrowingThemeResolver).execute(request())

            assertTrue(response.isOk, "theming must never break the account-action page")
            val ctx = assertNotNull(renderer.last)
            assertNull(ctx.resolvedThemeLight)
            assertNull(ctx.resolvedThemeDark)
        }

    private fun request(
        path: String = "/account-action",
        acceptLanguage: String? = null,
    ): GenericHttpRequest =
        GenericHttpRequest(
            method = "GET",
            path = path,
            headers =
                buildMap {
                    put("Host", "acme.example.test")
                    acceptLanguage?.let { put("Accept-Language", it) }
                },
        )

    private fun commandWith(
        renderer: AccountActionPageRenderer,
        themeResolver: ThemeResolver = FixedThemeResolver,
    ): AccountActionPageHttpEndpointCommandImpl =
        AccountActionPageHttpEndpointCommandImpl(
            execution = TestSessionExecution(tenantIdOverride = "acme"),
            configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(
                        servers = mapOf(
                            "default" to OAuth2ServerInstanceConfig(
                                webAuthn = WebAuthnLoginConfig(enabled = true),
                            ),
                        ),
                    ),
                ),
            pageRenderer = renderer,
            asInstanceIdProvider = object : OAuth2ServerInstanceIdProvider {
                override fun currentAsInstanceId(): String = "default"
            },
            baseUrlResolver = DefaultOAuth2ServerBaseUrlResolver(),
            themeResolver = Provider { themeResolver },
            featureResolver = Provider { EmptyFeatureResolver },
            softwareInstanceRegistry = Provider { EmptyRegistry },
        )

    private class RecordingRenderer : AccountActionPageRenderer {
        var last: AccountActionPageContext? = null

        override suspend fun render(
            ctx: AccountActionPageContext,
        ): IdkResult<AccountActionPageResponse, IdkError> {
            last = ctx
            return Ok(AccountActionPageResponse(html = "<html></html>", cspNonce = ctx.cspNonce))
        }
    }

    private object FixedThemeResolver : ThemeResolver {
        override suspend fun resolve(
            tenant: String,
            variant: ThemeVariant?,
            applicationId: String?,
            principalId: String?,
        ): ResolvedTheme =
            ResolvedTheme(
                tokens = mapOf("color.primary" to "#0085CA"),
                resolvedAt = kotlin.time.Instant.fromEpochSeconds(1),
                branding = BrandingMetadata(appName = "Acme", logoUrl = "https://cdn.example/acme.svg"),
            )
    }

    private object ThrowingThemeResolver : ThemeResolver {
        override suspend fun resolve(
            tenant: String,
            variant: ThemeVariant?,
            applicationId: String?,
            principalId: String?,
        ): ResolvedTheme = error("theme backend unavailable")
    }

    private object EmptyFeatureResolver : FeatureResolver {
        override suspend fun resolve(
            tenant: String,
            productType: ProductType,
            featureId: String,
            applicationId: String?,
            variant: ThemeVariant?,
        ): ResolvedFeature? = null
    }

    private object EmptyRegistry : SoftwareInstanceRegistry {
        override suspend fun list(tenantId: String, capabilityType: SoftwareCapabilityType) = emptyList<SoftwareInstance>()
        override suspend fun get(tenantId: String, instanceId: String): SoftwareInstance? = null
    }
}
