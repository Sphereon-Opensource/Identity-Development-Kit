/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.integration

import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.AuthorizationServerMode
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.config.OAuth2ServersConfigBinder
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Test-only `OAuth2ServersConfigProvider` that replaces the property-source-backed
 * [OAuth2ServersConfigBinder] in the session graph. Yields a single HOSTED server with OIDC
 * enabled + JWT tokens + the signing key alias the test context pre-generates.
 *
 * `issuer` is set to [ISSUER_URL] so id-token signing and id-token validation agree on a
 * stable issuer string. Tests that need to verify header-based base URL resolution (for
 * example the federation flow integration test) clear the `issuer` per case via
 * [overrideServer] so the OAuth2 AS HTTP adapters' `resolveBaseUrl()` helper falls back to
 * the X-Forwarded-Proto + Host headers.
 */
@ContributesTo(SessionScope::class, replaces = [OAuth2ServersConfigBinder::class])
interface OAuth2TestConfigModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideTestConfigProvider(): OAuth2ServersConfigProvider = TestOAuth2ServersConfigProvider()
}

@Inject
class TestOAuth2ServersConfigProvider : OAuth2ServersConfigProvider {
    private var config: OAuth2ServersConfig = defaultConfig()

    override fun getConfig(): OAuth2ServersConfig = config

    override fun getServer(id: String): OAuth2ServerInstanceConfig? = config.getServer(id)

    override fun getDefaultServer(): OAuth2ServerInstanceConfig = config.getDefaultServer()

    override fun resolveIssuer(
        serverId: String,
        tenantId: String,
    ): String = ISSUER_URL

    /**
     * Test-only seam: lets a single integration test swap the active server config (e.g. point
     * at an RSA key with `idTokenSigningAlgValuesSupported = null` to exercise the new alg
     * derivation) without rebuilding the AppGraph. The provider is `SingleIn(SessionScope)`
     * so this mutation is scoped to one test session.
     */
    fun overrideServer(server: OAuth2ServerInstanceConfig) {
        config = OAuth2ServersConfig(defaultServer = "default", servers = mapOf("default" to server))
    }

    fun resetToDefault() {
        config = defaultConfig()
    }

    companion object {
        const val ISSUER_URL = "https://op.test"

        fun defaultConfig(): OAuth2ServersConfig =
            OAuth2ServersConfig(
                defaultServer = "default",
                servers =
                    mapOf(
                        "default" to
                            OAuth2ServerInstanceConfig(
                                mode = AuthorizationServerMode.HOSTED,
                                issuer = ISSUER_URL,
                                oidc = FeaturePolicy.SUPPORTED,
                                idTokenSigningAlgValuesSupported = setOf("ES256"),
                            ),
                    ),
            )
    }
}
