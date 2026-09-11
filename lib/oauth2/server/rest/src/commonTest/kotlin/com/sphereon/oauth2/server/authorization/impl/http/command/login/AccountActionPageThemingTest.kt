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
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.WebAuthnLoginConfig
import com.sphereon.oauth2.server.authorization.impl.http.DefaultOAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.command.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import com.sphereon.software.registry.SoftwareInstanceRegistry
import com.sphereon.software.registry.model.SoftwareCapabilityType
import com.sphereon.software.registry.model.SoftwareInstance
import dev.zacsweers.metro.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AccountActionPageThemingTest {
    @Test
    fun rendersTenantBrandAndCspNonceOnTheAccountActionPage() =
        runTest {
            val command =
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
                    asInstanceIdProvider = object : OAuth2ServerInstanceIdProvider {
                        override fun currentAsInstanceId(): String = "default"
                    },
                    baseUrlResolver = DefaultOAuth2ServerBaseUrlResolver(),
                    themeResolver = Provider { FixedThemeResolver },
                    featureResolver = Provider { EmptyFeatureResolver },
                    softwareInstanceRegistry = Provider { EmptyRegistry },
                )
            val response =
                command.execute(
                    GenericHttpRequest(
                        method = "GET",
                        path = "/account-action",
                        headers = mapOf("Host" to "acme.example.test"),
                    ),
                )
            assertTrue(response.isOk)
            assertEquals(200, response.value.statusCode)
            val html = response.value.body.orEmpty()
            assertTrue(html.contains("Activate your Acme account"))
            assertTrue(html.contains("--color-primary:#0085CA"))
            assertTrue(html.contains("https://cdn.example/acme.svg"))
            assertTrue(html.contains("nonce="))
            assertTrue(html.contains("Save password and enroll passkey"))
            val csp = response.value.headers["Content-Security-Policy"].orEmpty()
            assertTrue(csp.contains("script-src"))
            assertTrue(csp.contains("nonce-"))
            assertFalse(html.contains("platform.example"))
        }

    @Test
    fun resolvesTheAccountActionBeforeCompletingActivation() =
        runTest {
            val command =
                AccountActionPageHttpEndpointCommandImpl(
                    execution = TestSessionExecution(tenantIdOverride = "acme"),
                    configProvider =
                        TestOAuth2ServersConfigProvider(
                            OAuth2ServersConfig(
                                servers = mapOf("default" to OAuth2ServerInstanceConfig()),
                            ),
                        ),
                    asInstanceIdProvider = object : OAuth2ServerInstanceIdProvider {
                        override fun currentAsInstanceId(): String = "default"
                    },
                    baseUrlResolver = DefaultOAuth2ServerBaseUrlResolver(),
                )

            val response =
                command.execute(
                    GenericHttpRequest(
                        method = "GET",
                        path = "/account-action",
                        headers = mapOf("Host" to "acme.example.test"),
                    ),
                )

            assertTrue(response.isOk)
            val html = response.value.body.orEmpty()
            val resolveCall = html.indexOf("fetch(\"/api/account-actions/v1/resolve\"")
            val completeCall = html.indexOf("fetch(\"/api/account-actions/v1/complete\"")
            assertTrue(resolveCall >= 0, "the page must open and validate the invitation token")
            assertTrue(completeCall > resolveCall, "activation must resolve the token before completing it")
            assertTrue(html.contains("activationReady.then(function () { return enrollPasskey().catch"))
            assertTrue(html.contains("id=\"submit-activation\" type=\"submit\" disabled"))
            assertTrue(html.contains("id=\"retry-activation\" type=\"button\""))
            assertTrue(html.contains("JavaScript is required to verify this single-use activation link"))
            assertTrue(!html.contains("form.hidden = true"), "an API failure must not leave the activation page without controls")
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
