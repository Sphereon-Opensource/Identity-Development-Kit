/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.conf.theme.core.model.BrandingMetadata
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.oauth2.common.config.LoginMethod
import com.sphereon.oauth2.server.authorization.provider.FederationLoginOption
import com.sphereon.oauth2.server.authorization.provider.LoginPageContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeutralLoginPageRendererTest {
    private val renderer = NeutralLoginPageRenderer()

    @Test
    fun rendersNeutralChooserWithoutSphereonChrome() =
        runTest {
            val html =
                renderer.render(
                    LoginPageContext(
                        asInstanceId = "primary",
                        tenantId = null,
                        sessionId = "pending-1",
                        returnUrl = "https://sts.example/authorize/callback?session_id=pending-1",
                        formActionBase = "https://sts.example",
                        showPasswordForm = false,
                        showWallet = true,
                        walletAuthorizationUrl = "https://bridge.example/auth/oid4vp/ui/start",
                        defaultMethod = LoginMethod.FEDERATION,
                        federationOptions = listOf(FederationLoginOption("surf", "eduID")),
                    ),
                ).value.html
            assertFalse(html.contains("Sphereon", ignoreCase = true))
            assertFalse(html.contains("name=\"password\""))
            assertTrue(html.contains("name=\"provider\" value=\"surf\""))
            assertTrue(html.contains("name=\"session_id\" value=\"pending-1\""))
            assertTrue(html.contains("name=\"return_url\" value=\"https://sts.example/authorize/callback?session_id=pending-1\""))
            assertTrue(html.contains("Institutional Account"))
            assertTrue(html.contains(">Wallet<"))
            assertTrue(html.contains("id=\"method-tab-federation\" checked"))
            assertTrue(html.contains("class=\"method-tab-action\" method=\"get\" action=\"https://bridge.example/auth/oid4vp/ui/start\""))
            assertTrue(html.contains("name=\"oauth_session_id\" value=\"pending-1\""))
            assertTrue(html.contains("data-tab-method=\"wallet\" type=\"submit\""))
            assertFalse(html.contains("Continue with wallet"))
        }

    @Test
    fun appliesThemeLogoAndPrimaryColor() =
        runTest {
            val html =
                renderer.render(
                    LoginPageContext(
                        asInstanceId = "primary",
                        tenantId = "acme",
                        sessionId = "pending-1",
                        returnUrl = "/authorize/callback?session_id=pending-1",
                        cspNonce = "abcdefghijklmnop",
                        resolvedThemeLight =
                            ResolvedTheme(
                                tokens = mapOf("color.primary" to "#0085CA"),
                                resolvedAt = kotlin.time.Instant.fromEpochSeconds(1),
                                branding = BrandingMetadata(appName = "Acme", logoUrl = "https://cdn.example/acme.svg"),
                            ),
                    ),
                ).value.html
            assertTrue(html.contains("--color-primary:#0085CA"))
            assertTrue(html.contains("https://cdn.example/acme.svg"))
            assertTrue(html.contains("alt=\"Acme\""))
        }

    @Test
    fun fallbackCssPreservesButtonTextContrastOnHover() {
        val css = renderer.staticAssets().first { it.path == "css/neutral-login.css" }.bytes.decodeToString()
        assertTrue(css.contains(".provider-button:hover"))
        assertTrue(css.contains("color:var(--color-on-surface)"))
    }
}
