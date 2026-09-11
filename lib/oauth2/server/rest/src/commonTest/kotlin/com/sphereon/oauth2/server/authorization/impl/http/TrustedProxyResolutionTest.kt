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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.impl.http.command.TestOAuth2ServersConfigProvider
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the precedence order in [effectiveScheme] / [effectiveHost] / [resolveBaseUrl] /
 * [buildFullUrl] that defends the AS against attacker-controlled `X-Forwarded-Proto` /
 * `X-Forwarded-Host` / `Host` rewrites.
 *
 * Order under test:
 *   1. configured `issuer` wins (operator-controlled, no header influence at all)
 *   2. else `trustForwardedHeaders=true` (legacy default) honors the header
 *   3. else hard-fall to `https` + `localhost` so a misconfigured deployment fails loudly
 *      rather than minting tokens with an attacker-controlled `iss` claim
 *
 * The header-rewrite attack worth pinning: an upstream actor with X-Forwarded-Proto/Host write
 * access can flip the issuer URL to a domain they control. With `issuer` set, the attack is
 * defused; with `issuer` unset and `trustForwardedHeaders=false`, the AS deliberately ignores
 * the headers so the operator notices via a non-functional deployment instead of via a token-
 * forgery incident.
 */
class TrustedProxyResolutionTest {
    private val attackerHeaders =
        mapOf(
            "X-Forwarded-Proto" to "http",
            "Host" to "attacker.example.com",
        )

    private fun req(headers: Map<String, String> = emptyMap()): GenericHttpRequest = GenericHttpRequest(method = "GET", path = "/authorize", headers = headers)

    private fun providerWith(
        issuer: String? = null,
        trustForwardedHeaders: Boolean = true,
    ): TestOAuth2ServersConfigProvider =
        TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers =
                    mapOf(
                        "default" to
                            OAuth2ServerInstanceConfig(
                                issuer = issuer,
                                trustForwardedHeaders = trustForwardedHeaders,
                            ),
                    ),
                defaultServer = "default",
            ),
        )

    @Test
    fun configuredIssuerOverridesAttackerHeaderScheme() {
        val provider = providerWith(issuer = "https://as.example.com/auth")
        val request = req(attackerHeaders)
        assertEquals("https", request.effectiveScheme(provider), "configured issuer scheme must beat X-Forwarded-Proto")
    }

    @Test
    fun configuredIssuerOverridesAttackerHostHeader() {
        val provider = providerWith(issuer = "https://as.example.com/auth")
        val request = req(attackerHeaders)
        assertEquals("as.example.com", request.effectiveHost(provider), "configured issuer host must beat Host header")
    }

    @Test
    fun configuredIssuerWithExplicitPortIsPreserved() {
        val provider = providerWith(issuer = "https://as.example.com:8443/auth")
        val request = req(attackerHeaders)
        assertEquals("as.example.com:8443", request.effectiveHost(provider))
    }

    @Test
    fun trustForwardedHeadersTrueAndNoIssuerHonorsHeader() {
        // Legacy posture: deployments that have not yet configured issuer keep working with a
        // single trusted reverse proxy in front. The startup WARN flags that they are exposed.
        val provider = providerWith(issuer = null, trustForwardedHeaders = true)
        val request =
            req(
                mapOf(
                    "X-Forwarded-Proto" to "https",
                    "Host" to "as.example.com",
                ),
            )
        assertEquals("https", request.effectiveScheme(provider))
        assertEquals("as.example.com", request.effectiveHost(provider))
    }

    @Test
    fun trustForwardedHeadersFalseAndNoIssuerRefusesHeader() {
        // Hardened posture: even if an attacker controls the headers, the AS will never derive
        // its scheme from them. Defaults to `https` + `localhost` so the deployment is broken
        // (cookies don't reach a public host) rather than tokens minted with attacker `iss`.
        val provider = providerWith(issuer = null, trustForwardedHeaders = false)
        val request = req(attackerHeaders)
        assertEquals("https", request.effectiveScheme(provider), "no-issuer + no-trust must default to https not the attacker's http")
        assertEquals("localhost", request.effectiveHost(provider), "no-issuer + no-trust must default to localhost not the attacker's host")
    }

    @Test
    fun resolveBaseUrlPrefersConfiguredIssuerEvenUnderAttackerHeaders() {
        val provider = providerWith(issuer = "https://as.example.com/auth")
        val request = req(attackerHeaders)
        assertEquals("https://as.example.com/auth", request.resolveBaseUrl(provider))
    }

    @Test
    fun resolveBaseUrlFallsBackToEffectiveSchemeAndHostWhenNoIssuer() {
        val provider = providerWith(issuer = null, trustForwardedHeaders = false)
        val request = req(attackerHeaders)
        assertEquals("https://localhost", request.resolveBaseUrl(provider))
    }

    @Test
    fun buildFullUrlPrefersConfiguredIssuerEvenUnderAttackerHeaders() {
        val provider = providerWith(issuer = "https://as.example.com/auth")
        val request = req(attackerHeaders).copy(path = "/token")
        // Path is appended to the configured issuer; attacker headers ignored.
        assertEquals("https://as.example.com/auth/token", request.buildFullUrl(provider))
    }

    @Test
    fun buildFullUrlWithNoIssuerAndNoTrustUsesEffectiveSchemeAndHost() {
        val provider = providerWith(issuer = null, trustForwardedHeaders = false)
        val request = req(attackerHeaders).copy(path = "/token")
        assertEquals("https://localhost/token", request.buildFullUrl(provider))
    }

    @Test
    fun buildFullUrlWithNullProviderKeepsLegacyHeaderFallbackForBackwardCompat() {
        // The nullable-provider overload exists for legacy call sites that have not yet adopted
        // `OAuth2ServersConfigProvider`. Behavior must match the pre-P0-K3 shape so a refactor
        // that removed a config-provider arg does not silently lose the fallback.
        val request = req(mapOf("X-Forwarded-Proto" to "https", "Host" to "as.example.com")).copy(path = "/token")
        assertEquals("https://as.example.com/token", request.buildFullUrl(configProvider = null))
    }
}
