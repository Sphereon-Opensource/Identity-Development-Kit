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

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.oauth2.server.authorization.provider.FederationLoginOption
import com.sphereon.oauth2.server.authorization.provider.LoginPageContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the IDK default Sphereon-branded login renderer produces the exact narrowed surface
 * the strategic boundary mandates: username + password only, no remember-me, no forgot-password,
 * no register, no social, no wallet / OID4VP tab, no `kc-*` Keycloak hooks. Also covers HTML
 * escaping of the login hint to guard the form from a stored XSS via the prefill.
 */
class SphereonBrandedLoginPageRendererTest {
    private val renderer = SphereonBrandedLoginPageRenderer()

    @Test
    fun rendersUsernamePasswordFormWithNoForbiddenWidgets() =
        runTest {
            val ctx =
                LoginPageContext(
                    asInstanceId = "default",
                    tenantId = null,
                    sessionId = "sess-123",
                    returnUrl = "https://example.org/authorize/callback?session_id=sess-123",
                    locale = "en",
                )

            val result = renderer.render(ctx)
            assertTrue(result.isOk, "render() should succeed")
            val html = result.value.html

            for (forbidden in FORBIDDEN_TOKENS) {
                assertFalse(
                    html.contains(forbidden, ignoreCase = true),
                    "Rendered login page must not contain '$forbidden' (strategic boundary).",
                )
            }
            assertTrue(html.contains("name=\"username\""), "Login form must include username field")
            assertTrue(html.contains("name=\"password\""), "Login form must include password field")
            assertTrue(html.contains("name=\"session_id\""), "Login form must echo session_id")
            assertTrue(html.contains("name=\"return_url\""), "Login form must echo return_url")
            assertTrue(html.contains("class=\"primary-button\""), "Login form must use primary-button class")
        }

    @Test
    fun escapesHtmlInLoginHint() =
        runTest {
            val ctx =
                LoginPageContext(
                    asInstanceId = "default",
                    tenantId = null,
                    sessionId = "sess-123",
                    returnUrl = "/authorize/callback?session_id=sess-123",
                    loginHint = "<script>alert('x')</script>",
                    locale = "en",
                )

            val html = renderer.render(ctx).value.html
            assertFalse(html.contains("<script>alert"), "Raw script tag must not appear in HTML output")
            assertTrue(
                html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"),
                "Login hint must be HTML-escaped",
            )
        }

    @Test
    fun escapesHtmlInSessionIdAndReturnUrl() =
        runTest {
            val ctx =
                LoginPageContext(
                    asInstanceId = "default",
                    tenantId = null,
                    sessionId = "sess\"><script>",
                    returnUrl = "/cb?x=\"&y=<",
                    locale = "en",
                )

            val html = renderer.render(ctx).value.html
            assertFalse(html.contains("\"><script>"), "Raw injected attribute must not appear in HTML output")
            assertTrue(html.contains("sess&quot;&gt;&lt;script&gt;"), "Session id must be HTML-escaped")
        }

    @Test
    fun staticAssetsContainCssAndImages() {
        val assets = renderer.staticAssets()
        val byPath = assets.associateBy { it.path }
        assertTrue("css/login.css" in byPath, "Renderer must publish login.css asset")
        assertTrue("img/sphereon-logo.svg" in byPath, "Renderer must publish sphereon-logo.svg asset")
        assertTrue("img/login-background.svg" in byPath, "Renderer must publish login-background.svg asset")
        assertEquals("text/css; charset=utf-8", byPath.getValue("css/login.css").contentType)
        assertEquals("image/svg+xml", byPath.getValue("img/sphereon-logo.svg").contentType)
        assertTrue(byPath.getValue("css/login.css").bytes.isNotEmpty(), "CSS payload must be non-empty")
    }

    @Test
    fun staticAssetsContainSelfHostedFonts() {
        // The login page must NOT load fonts from a third-party CDN: that leaks every login's IP
        // and timing to the CDN operator and breaks any reasonable `default-src 'self'` CSP. The
        // renderer therefore vendors Poppins (latin subset) under fonts/ and serves the four
        // weights the stylesheet references.
        val byPath = renderer.staticAssets().associateBy { it.path }
        listOf(400, 500, 600, 700).forEach { weight ->
            val path = "fonts/poppins-$weight.woff2"
            assertTrue(path in byPath, "Renderer must publish vendored font asset $path")
            val asset = byPath.getValue(path)
            assertEquals("font/woff2", asset.contentType, "Font asset $path must declare font/woff2")
            assertTrue(asset.bytes.isNotEmpty(), "Font asset $path must carry non-empty bytes")
            // Long-cache: per-deployment-immutable font payloads should survive at least a day so
            // every login does not re-download them.
            assertTrue(asset.maxAgeSeconds >= 86400, "Font asset $path must be long-cacheable")
        }
    }

    @Test
    fun renderedHtmlMustNotReferenceThirdPartyFontHosts() =
        runTest {
            val ctx =
                LoginPageContext(
                    asInstanceId = "default",
                    tenantId = null,
                    sessionId = "s",
                    returnUrl = "/cb",
                    locale = "en",
                )
            val html = renderer.render(ctx).value.html
            // Privacy + CSP regression guard: any future template change that re-introduces a
            // cross-origin font loader must trip this test rather than silently leak per-login
            // requests to a third-party CDN.
            listOf("fonts.googleapis.com", "fonts.gstatic.com", "use.typekit", "fonts.bunny").forEach { host ->
                assertFalse(html.contains(host), "Login template must not reference third-party font host $host")
            }
        }

    @Test
    fun unknownLocaleFallsBackToEnglish() =
        runTest {
            val ctx =
                LoginPageContext(
                    asInstanceId = "default",
                    tenantId = null,
                    sessionId = "s",
                    returnUrl = "/cb",
                    locale = "xx",
                )
            val html = renderer.render(ctx).value.html
            assertTrue(html.contains("Sign in"), "Unknown locale must fall back to English message bundle")
        }

    @Test
    fun displaysInvalidCredentialsErrorWhenSet() =
        runTest {
            val ctx =
                LoginPageContext(
                    asInstanceId = "default",
                    tenantId = null,
                    sessionId = "s",
                    returnUrl = "/cb",
                    errorMessage = "invalid_credentials",
                    locale = "en",
                )
            val html = renderer.render(ctx).value.html
            assertTrue(html.contains("input-error"), "Error block must be rendered when errorMessage is set")
            assertTrue(html.contains("Invalid username or password."), "Error message must be the localised text")
        }

    @Test
    fun emptyFederationListRendersNoBrokerBlock() =
        runTest {
            val html =
                renderer
                    .render(
                        LoginPageContext(
                            asInstanceId = "default",
                            tenantId = null,
                            sessionId = "s",
                            returnUrl = "/cb",
                            locale = "en",
                            federationOptions = emptyList(),
                        ),
                    ).value.html
            assertFalse(html.contains("federation-divider"), "Empty federation list must not emit divider")
            assertFalse(html.contains("federation-button"), "Empty federation list must not emit any buttons")
        }

    @Test
    fun federationOptionsRenderOneFormPerProvider() =
        runTest {
            val html =
                renderer
                    .render(
                        LoginPageContext(
                            asInstanceId = "default",
                            tenantId = null,
                            sessionId = "s",
                            returnUrl = "https://example.org/authorize/callback?session_id=s",
                            locale = "en",
                            federationOptions =
                                listOf(
                                    FederationLoginOption(id = "corp-saml", displayName = "Corporate SSO"),
                                    FederationLoginOption(id = "google", displayName = "Google"),
                                ),
                        ),
                    ).value.html
            // One form per provider, GET to /federation/authorize, hidden provider input.
            assertTrue(html.contains("federation-divider"), "Federation block must include the divider label")
            assertTrue(html.contains("Or continue with"), "English divider text must be present")
            assertTrue(html.contains("action=\"https://example.org/federation/authorize\""), "Form must POST to federation authorize endpoint")
            assertTrue(html.contains("name=\"provider\" value=\"corp-saml\""), "First provider must appear as form value")
            assertTrue(html.contains("name=\"provider\" value=\"google\""), "Second provider must appear as form value")
            assertTrue(html.contains("Continue with Corporate SSO"), "Button label must include provider display name")
            assertTrue(html.contains("Continue with Google"), "Button label must include each provider display name")
        }

    @Test
    fun federationOptionEscapesProviderIdAndName() =
        runTest {
            val html =
                renderer
                    .render(
                        LoginPageContext(
                            asInstanceId = "default",
                            tenantId = null,
                            sessionId = "s",
                            returnUrl = "/cb",
                            locale = "en",
                            federationOptions =
                                listOf(
                                    FederationLoginOption(
                                        id = "evil\"><script>alert(1)</script>",
                                        displayName = "<img src=x onerror=alert(1)>",
                                    ),
                                ),
                        ),
                    ).value.html
            assertFalse(html.contains("<script>alert(1)"), "Raw script payload must not survive escaping")
            assertFalse(html.contains("<img src=x onerror"), "Raw HTML payload must not survive escaping")
            assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), "Provider id must be HTML-escaped in form value")
            assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"), "Display name must be HTML-escaped on the button")
        }

    @Test
    fun federationDividerLocalisesToDutch() =
        runTest {
            val html =
                renderer
                    .render(
                        LoginPageContext(
                            asInstanceId = "default",
                            tenantId = null,
                            sessionId = "s",
                            returnUrl = "/cb",
                            locale = "nl",
                            federationOptions = listOf(FederationLoginOption(id = "p", displayName = "P")),
                        ),
                    ).value.html
            assertTrue(html.contains("Of ga verder met"), "Dutch divider label must be applied")
            assertTrue(html.contains("Doorgaan met P"), "Dutch button-prefix label must be applied")
        }

    companion object {
        private val FORBIDDEN_TOKENS =
            listOf(
                "doForgotPassword",
                "doRegister",
                "kc-social",
                "kcLoginClass",
                "login-pf",
                "oid4vp",
                "walletDescription",
                "rememberMe",
                "social-providers",
                "tab-content",
                "wallet-view",
            )
    }
}
